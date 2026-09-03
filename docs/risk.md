# FinLedger 风控引擎

## 设计边界

风控只评估和记录，不直接更新账户余额。每条规则实现 `RiskRule`，接收包含 userId、交易号、
交易类型、账户、金额和 UTC 时间的 `RiskContext`，返回：

```text
ruleCode + ruleName + riskLevel + decision + reason + metadata
```

`RiskEngine` 由 Spring 注入规则集合，按阶段运行并选择最严格结论：

```text
PASS < REVIEW < REJECT
```

| 结论 | 业务行为 |
| --- | --- |
| PASS | 允许进入 PENDING |
| REVIEW | 保存事件并冻结资金，等待后续清算或撤销 |
| REJECT | 保存 FAILED 订单和风险事件，不冻结资金 |

## 演示规则与默认阈值

| 规则 | 默认 PASS | 默认 REVIEW | 默认 REJECT | 数据源 |
| --- | --- | --- | --- | --- |
| HIGH_AMOUNT | `< 10,000` | `10,000 ≤ amount < 50,000` | `amount ≥ 50,000` | 当前请求 |
| HIGH_FREQUENCY | 60 秒内 `count ≤ 5` | `6—10` | `count > 10` | Redis Lua |
| DAILY_LIMIT | UTC 当日累计 `< 30,000` | `30,000—50,000` | `> 50,000` | MySQL 订单 |

这些数值只是项目演示配置，不代表任何真实银行、券商或支付机构规则。阈值由
`finledger.risk.*` / 环境变量配置，启动时校验 review 不得大于 reject。

## 执行阶段

- `PRE_TRANSACTION`：HIGH_FREQUENCY。它依赖外围 Redis，放在持有数据库锁之前以缩短事务；
- `IN_TRANSACTION`：HIGH_AMOUNT 和 DAILY_LIMIT。执行器已创建处理订单并锁住同一用户与账户，
  日累计查询和接受新订单不能被该用户的并发请求穿透。

REJECT 不能在事务内部直接抛异常，否则 FAILED 订单和 risk_event 会一起回滚。执行器先提交
拒绝事实，外层再返回 `RiskRejectedException`。

## Redis 高频规则

Lua 脚本原子执行 `INCR`，并只在第一次计数时设置 PEXPIRE，key 包含 userId。Redis 不可用时
规则记录告警并 fail-open；这是明确的可用性选择，短期频率信号会降级，但账户锁、余额约束、
订单、日限额和流水仍由 MySQL 保障。Redis 从不保存权威余额或最终风控结论。

## UTC 自然日累计

`DAILY_LIMIT` 查询 `[UTC 当日 00:00, 下一日 00:00)` 内状态为 SUCCESS、PENDING 或 SETTLED
的本人转出订单，再加当前金额得到 projected amount。左闭右开区间避免跨日重复统计；同一用户
行锁让并发交易串行完成累计判断。

## Risk Event

V5 后每条非 PASS 事件保存：transaction ID/No、userId、amount、ruleCode、riskLevel、decision、
reason、metadata_json 和 createdAt。`(business_no, rule_code)` 唯一约束是重复记录的最终仲裁者。
metadata 包括规则名、阈值、计数或时间窗口，供排查和 AI 解释使用。

`GET /api/risk-events` 支持 `transactionId`、`businessNo`、`decision`、`ruleCode`、`page`、`size`。
Service 无条件附加 JWT userId，因此知道别人的交易号或 ID 也无法查询其风险事件。

## 扩展规则

新增规则时实现 `RiskRule`、选择执行阶段、提供稳定 code/name/reason/metadata，并为 PASS、边界
REVIEW、边界 REJECT 和多规则最严聚合编写测试。规则不能持有账户 Mapper 并自行改余额；所有
资金动作仍由统一交易执行器根据聚合结果处理。


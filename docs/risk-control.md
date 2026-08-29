# 风控规则与事务边界

本文件保留为旧链接兼容入口；当前完整设计、阈值、事件字段和扩展方式见 [risk.md](risk.md)。

## 规则框架

风控是独立 `risk` 模块，不把条件判断堆进 TransferService。每条规则实现 `RiskRule`：

```java
RiskEvaluation evaluate(RiskContext context);
```

Spring 注入全部策略，`RiskEngine` 按阶段执行并聚合为最严格结论：

```text
PASS < REVIEW < REJECT
```

| 结论 | 行为 |
| --- | --- |
| `PASS` | 允许创建 PENDING 交易 |
| `REVIEW` | 允许冻结并继续，但保存风险事件供查询与解释 |
| `REJECT` | 保存 FAILED 订单和风险事件，不冻结资金 |

## 第一版三条规则

| 规则 | 数据源 | 默认条件 | 决策 |
| --- | --- | --- | --- |
| `HIGH_AMOUNT` | 当前请求 | 10,000 起 REVIEW；50,000 起 REJECT | REVIEW / REJECT |
| `HIGH_FREQUENCY` | Redis Lua 窗口计数 | 60 秒 6—10 次 REVIEW；>10 次 REJECT | REVIEW / REJECT |
| `DAILY_LIMIT` | MySQL 已接受订单 | UTC 日累计 30,000—50,000 REVIEW；>50,000 REJECT | REVIEW / REJECT |

阈值、窗口和 `enabled` 总开关均由 `finledger.risk.*` 配置绑定到 `RiskProperties`，Java 代码
没有写死运行值。

## 为什么分成两个阶段

HIGH_FREQUENCY 依赖外围 Redis，在进入数据库事务前执行以减少持锁时间。HIGH_AMOUNT 和
DAILY_LIMIT 在事务内执行；DAILY_LIMIT 基于可靠订单事实，而且“查询累计额”和“接受新订单”
不能被同一用户的并发请求穿透，因此先锁住用户行。

风险事件和对应订单在同一事务提交。REJECT 不能直接从事务方法抛出并回滚，否则拒绝订单和
审计事件也会消失；执行器先提交 FAILED/REJECT 事实，外层服务再向客户端抛出
`RiskRejectedException`。

## Redis 与 MySQL 的职责

Redis 只提供短期频率信号，Lua 把计数和首次过期设置合成原子操作。Redis 不可用时规则记录
警告并 fail-open，让 MySQL 核心交易继续按账户锁、余额约束和 DAILY_LIMIT 工作。这是明确的
可用性选择：短期高频检测会降级，但绝不能因为缓存故障破坏资金事务。

MySQL 始终保存账户、订单、每日累计依据和 `risk_event`。Redis 不能作为余额、限额累计或
最终风控结论的唯一事实源。

## 可追溯风险事件

每条非 PASS 判断保存 user、交易 ID/No、金额、规则编码、风险级别、决策、原因、JSON 元数据和
时间。查询接口强制用 JWT `sub` 绑定 `user_id`；即使知道别人的交易号，也查不到对方的事件。

AI 风控解释同样先由 Java 查询本人订单和风险事件，再把授权后的结构化数据交给确定性解释器
或外部模型。模型不能重新打分、改变决策、执行 SQL 或调用资金操作。

## 扩展新规则

新增规则只需实现 `RiskRule`、选择 PRE_TRANSACTION 或 IN_TRANSACTION 阶段并增加测试。规则
不应直接更新订单或余额；它返回评估，由统一执行器决定是否保存事件和继续交易，从而保持职责
边界与一致的聚合语义。

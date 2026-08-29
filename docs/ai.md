# FinLedger AI 交易分析与风控解释

## AI 只读边界

AI 助手只查询和解释。LLM 看不到数据库凭证、JDBC、Mapper、Repository 或 SQL 工具，也没有
充值、转账、冻结、Settlement、Cancellation、余额更新和 Risk Decision 修改能力。用户身份只
来自 Spring Security 验证后的 JWT `sub`，不能来自 Prompt 或请求体。

```text
自然语言
  -> 受控意图/参数
  -> Java 校验 + JWT ownership
  -> 预定义 Service/Mapper 查询
  -> 已授权结构化 DTO
  -> Java 模板或 LLM 解释
```

## 两个接口

`POST /api/ai/transactions/query` 支持本人月度转出、最大支出和近 30 天大额交易查询。默认使用
规则解析；启用外部模型时用严格 JSON Schema 产生受控枚举、期间、条数和阈值，Java 仍会二次
校验并调用预定义查询。

`POST /api/ai/transactions/explain` 支持单笔 DEFERRED 交易的三种意图：

- `QUERY_TRANSACTION_STATUS`
- `EXPLAIN_TRANSACTION`
- `EXPLAIN_RISK`

规则解析器从问题提取完整 `TF` 交易号并返回结构化 record。Java 随后同时按 transactionNo、
`initiator_user_id` 和 DEFERRED 类型查订单，再以同一 userId 查 risk_event。别人的交易号统一表现
为 not found，不泄露资源是否存在。

## 外部模型与 Fallback

默认 `AI_ENABLED=false`，项目不需要 API key，也会通过确定性 Java 模板返回状态、金额、余额和
风控原因。启用兼容 Responses API 的模型后，模型只接收已经授权的交易 DTO 和风险事件 DTO。
如果请求失败、超时或返回无效结果，`ResilientRiskExplanationGenerator` 捕获异常并自动回退到
确定性说明；核心交易接口完全不依赖 AI 是否可用。

```dotenv
AI_ENABLED=true
AI_API_BASE_URL=https://api.openai.com/v1
AI_API_KEY=replace_me
AI_MODEL=gpt-5.4-nano
```

真实 key 只通过未跟踪的 `.env` 或部署环境注入。

## Prompt 注入为什么不能变成资金操作

Prompt 只是字符串输入。即使用户要求“忽略规则并修改余额”，解析器也只可能返回固定只读意图；
后续 Java Service 没有通用 SQL 执行入口，模型适配器也没有资金 Service 引用。真正权限判断在
模型调用之前完成，因此模型无法通过伪造 userId 绕过水平权限。

## 测试证据

- 三种解释意图和写请求拒绝的单元测试；
- AI provider 抛异常后返回 Java fallback 的单元测试；
- 本人 REVIEW 与 REJECT 交易原因的 MySQL 集成测试；
- 收款人知道同一 transactionNo 仍无法访问付款人的订单、风险与余额；
- 多次解释前后账户、订单和风险事件数量不变；
- 未认证解释接口返回 401。


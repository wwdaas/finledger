# AI 交易分析助手

本文件保留为旧链接兼容入口；当前三种解释意图、JWT 边界和 provider fallback 见
[ai.md](ai.md)。

AI 助手是只读功能，提供交易分析 `/api/ai/transactions/query` 和交易状态/风控解释
`/api/ai/transactions/explain`。分析流程固定为：

```text
自然语言 -> 受控意图 -> Java 校验 -> 带 user_id 的预定义查询
         -> 结构化结果 -> 自然语言解释
```

支持三种意图：本月/上月转出统计、期间最大支出、最近 30 天大额交易。模型既不接收
数据库连接，也不能生成或执行 SQL；所有 mapper 查询都预先写在 Java 中，并强制包含
JWT 用户 ID。系统没有向 AI 暴露充值、转账或余额更新工具。

状态解释流程为：

```text
问题 -> 受控解释意图 + transactionNo -> JWT userId -> Java 查询本人 DEFERRED 订单
     -> Java 查询本人 risk_event -> 授权结构化数据 -> 只读解释
```

交易号可以来自 Prompt，用户 ID 绝不能来自 Prompt 或请求体。`DeferredTransferQueryService`
同时限制交易号、`initiator_user_id` 和订单类型，风险事件也绑定同一 userId；因此知道其他用户
的交易号仍无法查询其交易、风控记录或资金状态。风险决策已经由 Java 规则产生，AI 不能重新
判定 PASS/REVIEW/REJECT。

解释支持 `QUERY_TRANSACTION_STATUS`、`EXPLAIN_TRANSACTION`、`EXPLAIN_RISK`。默认
`AI_ENABLED=false`，使用规则解析和确定性解释，方便无 API key 演示。启用后，
应用通过 OpenAI Responses API 获取意图和解释。分析意图使用严格 JSON Schema，之后仍由
Java 二次限制枚举、时间窗口、最多 10 条结果和金额精度。外部解释调用失败时自动回退到 Java
基础说明，核心交易不依赖 AI。官方 Structured Outputs 文档：
<https://developers.openai.com/api/docs/guides/structured-outputs>。

外部模型配置：

```dotenv
AI_ENABLED=true
AI_API_BASE_URL=https://api.openai.com/v1
AI_API_KEY=replace_me
AI_MODEL=gpt-5.4-nano
```

API key 只通过环境变量注入，不写入 Git。若使用其他兼容服务，必须确认其 `/responses`
和严格 JSON Schema 行为兼容；否则应新增独立 provider adapter，不能弱化 Java 校验。

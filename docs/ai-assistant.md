# AI 交易分析助手

AI 助手是只读功能，接口为 `POST /api/ai/transactions/query`。流程固定为：

```text
自然语言 -> 受控意图 -> Java 校验 -> 带 user_id 的预定义查询
         -> 结构化结果 -> 自然语言解释
```

支持三种意图：本月/上月转出统计、期间最大支出、最近 30 天大额交易。模型既不接收
数据库连接，也不能生成或执行 SQL；所有 mapper 查询都预先写在 Java 中，并强制包含
JWT 用户 ID。系统没有向 AI 暴露充值、转账或余额更新工具。

默认 `AI_ENABLED=false`，使用规则解析和确定性解释，方便无 API key 演示。启用后，
应用通过 OpenAI Responses API 获取意图和解释。意图使用严格 JSON Schema，之后仍由
Java 二次限制枚举、时间窗口、最多 10 条结果和金额精度。官方 Structured Outputs 文档：
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

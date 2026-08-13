# 统一错误响应

所有业务异常继承 `BusinessException`，同时携带 HTTP 状态码和稳定业务错误码。
`GlobalExceptionHandler` 统一处理业务异常、Bean Validation、JSON 解析、参数类型、
数据库约束和未预期异常。Spring Security 过滤器中的 401/403 也使用相同结构。

示例：

```json
{
  "timestamp": "2026-08-13T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_BALANCE",
  "message": "Insufficient balance in account: 1",
  "path": "/api/transfers"
}
```

字段校验失败还会返回 `fieldErrors`。数据库和未知异常只向客户端返回安全的概括信息，
详细堆栈仅记录在服务端日志，避免泄露表名、SQL 和凭证。

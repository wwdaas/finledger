# JWT 认证与权限边界

注册和登录接口公开，其余业务接口要求 `Authorization: Bearer <token>`。
登录使用 BCrypt 校验密码，成功后由应用签发 HS256 JWT。token 包含：

- `sub`：不可由客户端请求参数覆盖的用户 ID；
- `username`：展示和审计辅助信息；
- `iss`、`iat`、`exp`、`jti`：签发者、签发时间、过期时间和唯一标识；
- `scope=USER`：普通用户权限。

Spring Security Resource Server 在控制器执行前验证签名、签发者和过期时间。
账户、充值、转账和流水控制器只从已验证 JWT 的 `sub` 读取当前用户，不再信任
`X-User-Id`。

JWT 是无状态凭证，默认有效期一小时。用户状态在签发时检查；如果以后需要即时登出或
立刻禁用已签发 token，可在 Redis 中保存短期 `jti` 黑名单，但这不是账户余额或交易
一致性的组成部分。

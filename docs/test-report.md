# FinLedger Phase 14 测试与工程回归报告

执行日期：2026-08-29  
执行环境：Java 17、Maven Wrapper、Docker Desktop、MySQL 8.4.11 Testcontainers

## 回归结论

`./mvnw clean verify` 构建成功，共执行 92 个测试：

| 类型 | 数量 | 结果 |
| --- | ---: | --- |
| 单元测试与 MockMvc Web/Security 测试 | 56 | 全部通过 |
| MySQL Testcontainers 集成测试 | 36 | 全部通过 |
| 合计 | 92 | 0 失败、0 错误、0 跳过 |

集成测试中，`FinancialFlowIntegrationTest` 有 35 个场景，另有 1 个真实迁移测试。
测试使用 MySQL 8.4.11，不使用 H2 代替事务、行锁、唯一约束和 CHECK 约束行为。

## 核心回归范围

- 充值、即时转账、延迟交易冻结、清算、撤销的正常路径；
- 清算流水失败、撤销流水失败、冻结记录失败时的事务整体回滚；
- 并发超扣、重复 Idempotency-Key、清算与撤销竞争；
- 可用余额、冻结余额和逻辑总余额守恒；
- 风控规则 PASS / REVIEW / REJECT 边界、Redis 故障降级、UTC 日累计窗口；
- 风控事件唯一约束、JSON 元数据、组合筛选、分页和用户隔离；
- JWT 认证、账户归属、交易归属和 AI 查询权限；
- 三种 AI 交易解释意图、只读约束及模型不可用时的确定性降级。

## Docker 与运行验证

- `docker compose config --quiet`：配置语法通过；全新环境需先从 `.env.example` 创建
  未跟踪的 `.env` 并填写密码；
- `docker build --tag finledger:phase4-15 .`：多阶段镜像构建成功；
- 现有 MySQL 数据卷已增量执行 `V5__enhance_risk_events.sql`，未删除或重建数据卷；
- 更新运行 JAR 后，`finledger-app` 健康检查为 `healthy`；
- `GET http://127.0.0.1:8080/api/health` 返回 `{"status":"UP","service":"finledger"}`。

## 安全与仓库检查

- Git 跟踪文件未发现私钥头、常见 OpenAI/GitHub/AWS 凭证格式；
- `.env` 未被 Git 跟踪，示例文件只保存占位值；
- AI API Key、JWT Secret、MySQL 和 Redis 密码均通过环境变量注入；
- AI 解释链路只接收 Java Service 完成权限校验后的 DTO，不持有 Mapper、SQL 或资金写服务；
- GitHub Actions 已配置 Java 17、`mvn verify` 和 Docker 镜像构建。远端 CI 状态在发布
  Pull Request 后单独确认。

## 可复现命令

```bash
./mvnw clean verify
docker compose config --quiet
docker build --tag finledger:phase4-15 .
curl --fail http://127.0.0.1:8080/api/health
```

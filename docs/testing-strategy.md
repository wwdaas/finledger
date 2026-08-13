# 测试策略

测试按风险分层：

- JUnit 5 + Mockito：快速验证金额计算、服务分支、控制器参数和依赖交互；
- Spring MVC 测试：验证状态码、JWT 入口和统一错误结构；
- Testcontainers + MySQL 8.4：验证真实 InnoDB 事务、行锁、唯一约束和触发器故障回滚。

`FinancialFlowIntegrationTest` 使用一次性 MySQL 容器依次执行正式 V1、V2、V3 脚本，覆盖正常转账、
余额不足、非法金额、账户不存在、流水写入故障回滚、并发超扣、并发重复幂等 key、
认证和账户权限。并发用两个真实线程同时释放起跑闩锁，不用串行调用模拟竞争。

增强测试还覆盖：1000 冻结 300 后可用 700/冻结 300、余额不足冻结整体回滚、正常清算、正常
撤销、重复清算/取消失败，以及同一 PENDING 订单被两个线程同时清算和撤销时恰好一个成功。
最终断言不只检查状态，还核对冻结额归零、来源和目标总资金守恒、资金变动与双边流水数量。

风控单元测试分别验证 HIGH_AMOUNT、HIGH_FREQUENCY（含 Redis 故障降级）和 DAILY_LIMIT；
集成测试验证 REVIEW/REJECT 订单与 risk_event 同步落库、日限额拒绝不新增冻结额。

`AccountBalanceMigrationIntegrationTest` 先只执行 V1 并写入 `balance = 1000.00` 的旧账户，
再依次执行 V2、V3，验证最终 `available_balance = 1000.00`、`frozen_balance = 0.00` 且旧列已
移除。双余额集成场景还验证充值不改变冻结额、普通转账只检查可用额，以及 MySQL 拒绝任何
负 available/frozen 更新。兼容性测试还证明升级前保存的 `fromBalance/toBalance` 幂等快照仍可
反序列化，并在 Java 中明确映射为来源/目标总资金。当前完整测试套件共 66 项。

AI 集成测试先完成真实充值和转账，再分别以付款人与收款人的用户 ID 查询统计，证明
分析服务只返回当前 JWT 用户可见的数据。模型结构化输出转换和只读指令识别由独立单元
测试覆盖。风控解释测试让付款人查询 HIGH_AMOUNT 事件，再断言收款人使用相同 transactionNo
得到 not found，证明交易、风险和资金状态没有水平越权。外部 provider 不进入数据库一致性测试。

测试配置关闭 Redis 限流，因为这一组测试的目标是数据库一致性；Redis Lua 限流由独立
单元测试和 Compose 实例验收。生产 MySQL 或开发库不会被集成测试读写。

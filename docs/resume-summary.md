# FinLedger 简历项目摘要

## 一句话版本

FinLedger 是基于 Java 17、Spring Boot 3、MyBatis-Plus、MySQL 8 和 Redis 的模块化单体模拟
资金账户系统，重点实现双余额生命周期、事务并发一致性、数据库幂等、可解释风控和安全只读 AI。

## 简历项目描述

- 使用 Spring `@Transactional` 和 InnoDB `SELECT ... FOR UPDATE` 实现双账户原子转账，按账户
  ID 固定顺序加锁降低反向转账死锁风险，并用双线程 MySQL Testcontainers 用例验证并发不超扣；
- 设计 `available/frozen` 双余额与 `PENDING → SETTLED/CANCELLED` 状态机，实现资金冻结、清算、
  撤销解冻及异常整体回滚，以订单行锁和条件状态更新解决 SETTLE/CANCEL 竞争；
- 基于 `Idempotency-Key`、SHA-256 请求摘要和 MySQL 联合唯一约束实现并发幂等，支持同请求结果
  回放和同 key 不同参数冲突检测；
- 基于 Strategy 模式构建 HIGH_AMOUNT、HIGH_FREQUENCY、DAILY_LIMIT 三级风控规则，统一聚合
  PASS/REVIEW/REJECT，并持久化包含金额与 JSON 元数据的可追溯 Risk Event；
- 构建只读 AI 交易解释链路，以受控意图、JWT ownership、预定义查询和 provider fallback 限制
  LLM 权限，确保模型不能执行 SQL、修改余额或改变风控结论；
- 建立 92 项 JUnit 5、Mockito、MockMvc 和 MySQL 8 Testcontainers 测试，覆盖事务回滚、并发超扣、
  重复请求、终态竞争、风控边界与数据隔离，并通过 Docker 多阶段镜像和 GitHub Actions 回归。

## 一分钟面试介绍

这个项目不是支付渠道，而是用模拟资金业务练习 Java 后端一致性。我把立即转账中的幂等占位、
双账户更新、订单、双边流水放在一个 MySQL 事务里；余额检查在行锁之后完成，并统一账户加锁顺序。
在此基础上增加 available/frozen 双余额与待处理交易状态机，解决冻结、清算、撤销及终态并发竞争。
风控使用 Strategy 规则聚合并保存审计事件，Redis 只提供可降级高频信号。最后增加只读 AI 解释，
所有权限和查询仍由 Java 控制。92 个测试中包含 36 个真实 MySQL 集成场景，用故障注入和双线程
竞争直接证明回滚、锁和唯一约束行为。

## 不应使用的表述

不要声称“银行级”“券商生产系统”“百万 TPS”“生产级风控”或“彻底消除死锁”。本项目不连接
真实银行、银行卡、支付渠道或券商核心，不处理真实资金，也没有做吞吐量压测。这些边界在 README
中明确声明。


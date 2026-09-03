# 资金冻结设计

## Freeze Definition

独立资金冻结的含义是把同一账户内的一部分资金从可用状态迁移到冻结状态：

```text
available_balance -= amount
frozen_balance    += amount
total_before       = total_after
```

它不是转账或最终扣款。冻结后这部分资金仍属于当前账户，但不能再参加普通转账。本阶段只创建
`FROZEN` 记录，不实现解冻、清算或取消。

入口为 `POST /api/accounts/{accountId}/freezes`，用户身份只取自已验证 JWT 的 `sub`。请求体不
接受 `userId`，即使客户端知道其他人的 accountId，`AccountService.lockOwnedAccount` 也会拒绝
水平越权。

```http
POST /api/accounts/1/freezes
Authorization: Bearer <JWT>
Idempotency-Key: freeze-demo-001
Content-Type: application/json

{
  "amount": 300.00,
  "businessType": "TRADE",
  "remark": "Pending transaction"
}
```

## Transaction Boundary

`FreezeExecutor.execute` 是 Spring 事务代理调用的公开方法，以下 SQL 在一个 MySQL 本地事务中：

```text
INSERT idempotency_record(PROCESSING)
  -> SELECT account ... FOR UPDATE
  -> 校验归属、状态和锁定后的 available_balance
  -> UPDATE account
       SET available = available - amount,
           frozen    = frozen + amount
       WHERE id = ? AND available >= amount
  -> INSERT fund_freeze(FROZEN)
  -> INSERT fund_movement_record(FREEZE)
  -> UPDATE idempotency_record(SUCCESS, response_snapshot)
  -> COMMIT
```

任何运行时异常都会使幂等占位、双余额更新、冻结记录、资金变化记录和成功响应一起回滚。测试用
数据库触发器强制 `fund_freeze` 插入失败，并直接查询数据库证明余额没有停留在“已冻结但无订单”
的中间状态。

## Concurrency

`@Transactional` 解决的是一组操作共同提交或共同回滚，不会自动阻止两个事务读取同一个旧余额。
冻结在事务内通过 `SELECT ... FOR UPDATE` 锁住账户行，等待者只能在前一个事务提交后读取最新
available 值并重新校验。

账户更新同时带 `AND available_balance >= amount`。行锁是业务并发的主要控制手段，条件更新是
数据库侧的额外防线：即使未来代码重构遗漏了某个校验，更新影响行数也不会伪装成成功。两者再
配合 `available_balance >= 0` 和 `frozen_balance >= 0` 的 CHECK 约束形成分层保护。

Testcontainers 用两个真实线程同时对 `available=100` 的账户冻结 80，最终只允许一笔成功，
结果必须是 `available=20`、`frozen=80`，而不是负余额或冻结 160。

## Idempotency

冻结是资金写操作，浏览器重复点击或网络超时重试不能造成二次冻结。实现复用现有
`idempotency_record`、SHA-256 请求摘要、MySQL 联合唯一约束和响应回放：

- 唯一维度为 `(user_id, FUND_FREEZE, idempotency_key)`；
- 摘要覆盖 accountId、规范化 amount、businessType 和 remark；
- 同 key 同参数返回第一次的 `FreezeResponse`；
- 同 key 不同参数返回 HTTP 409；
- 并发同 key 由数据库唯一约束决定唯一执行者，另一个请求等待后回放结果。

幂等记录不放在 Redis，因为 Redis 不是资金事实源，过期、淘汰或故障切换都不能导致重复冻结。

## Records and Invariants

`fund_freeze` 是可追踪业务事实，保存外部冻结号、用户、账户、金额、业务类型、备注和状态。
立即转账用的 `transfer_order` 强制具有来源和目标账户，语义不适合表达单账户内部状态迁移，因此
没有复用。`transaction_record` 表达真实总资金流入/流出；冻结总额不变，所以使用已有
`fund_movement_record` 保存 available/frozen/total 的前后快照。

每次成功冻结必须满足：

```text
amount > 0
available_after >= 0
frozen_after >= 0
available_before - amount = available_after
frozen_before + amount = frozen_after
total_before = total_after
恰好 1 条 fund_freeze
恰好 1 条 FUND_FREEZE / FREEZE movement
```

Java 使用 `BigDecimal` 并统一为两位小数，MySQL 使用 `DECIMAL(19,2)`。金额数值比较用
`compareTo`，因为 `BigDecimal.equals` 还会比较 scale。

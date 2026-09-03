# FinLedger 并发控制

## 为什么事务还需要锁

`@Transactional` 保证一组 SQL 原子提交或回滚，但不自动保证两个事务不会同时读取旧余额。
若 A 有 100 元，两线程都先读到 100，再分别转出 80，就可能都通过业务校验。FinLedger 的
资金主路径在事务内使用 `SELECT ... FOR UPDATE`，等待者获得锁后必须重新读取并校验最新余额。

## 悲观锁主路径

```sql
SELECT * FROM account WHERE id = ? FOR UPDATE;
```

InnoDB 会锁住命中的账户记录，锁持有到事务提交或回滚。代码不会在事务外读取余额后把陈旧值
带进事务；账户状态、归属和余额判断都基于锁定后的实体。更新 SQL 和非负 CHECK 再作为防御层，
但不能替代锁后的业务校验。

## 固定账户加锁顺序

A→B 和 B→A 如果按业务方向分别先锁 A、先锁 B，会形成循环等待。项目统一按账户 ID：

```text
first  = min(fromAccountId, toAccountId)
second = max(fromAccountId, toAccountId)
```

无论业务方向如何都先锁 first，再锁 second，降低这条典型死锁路径。它不能保证整个系统绝不
死锁：其他表、索引和未来代码路径仍可能引入不同锁顺序。生产增强应识别数据库死锁，并在
Idempotency-Key 保护下对完整事务做少量、有界重试。

## 状态竞争

Settlement 与 Cancellation 同时操作一笔 PENDING 订单时，双方先竞争同一订单行锁。获胜者
提交终态后，等待者会读取到 SETTLED 或 CANCELLED，状态机立即拒绝。最终更新还要求：

```sql
UPDATE transfer_order
SET status = ?
WHERE id = ? AND status = 'PENDING';
```

影响行数不是 1 就抛异常并回滚。这是“行锁 + 状态机 + 条件更新”的三层保护。

## 乐观锁对照

`account.version` 和版本条件更新用于展示另一种方案：

```sql
UPDATE account
SET available_balance = ?, version = version + 1
WHERE id = ? AND version = ?;
```

更新 0 行表示发生冲突，调用者必须重新读取、重新校验并有限重试。乐观锁适合冲突低、事务短且
重试成本可控的场景；资金扣减冲突较敏感，本项目主要采用悲观锁，避免高冲突下的重试风暴。

## 测试方式

Mockito 无法证明数据库锁。集成测试使用两个真实线程、`CountDownLatch` 同时起跑和 MySQL 8.4
Testcontainers，验证：100 元并发转出两笔 80 元最多一笔成功；并发冻结不会超冻；相同幂等 key
只执行一次；SETTLE 与 CANCEL 竞争时只有一个终态且余额守恒。


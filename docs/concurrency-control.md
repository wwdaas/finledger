# Concurrency control (Phase 8)

> This historical note is retained for old links. The current Chinese design and test evidence are in
> [concurrency.md](concurrency.md).

## Project choice

FinLedger uses pessimistic row locks for the primary transfer implementation:

```sql
SELECT ... FROM account WHERE id = ? FOR UPDATE;
```

Both accounts are always locked in ascending account-ID order, independent of the
business direction. Therefore A to B and B to A request locks in the same order.
This removes the most obvious circular-wait pattern, although applications must
still be prepared for MySQL to detect other deadlocks.

The lock is effective only inside the surrounding Spring transaction. It is held
until commit or rollback, so the balance check and both updates observe a stable
pair of account rows.

## Pessimistic versus optimistic locking

| Property | Pessimistic (`FOR UPDATE`) | Optimistic (`version`) |
| --- | --- | --- |
| Conflict handling | Waits for the current holder | Update affects zero rows, caller retries |
| Best fit | High-value, short, conflict-sensitive changes | Read-heavy, low-conflict changes |
| Main cost | Lock waits and possible deadlocks | Retry logic and possible retry storms |
| FinLedger use | Primary transfer path | Retained as a learning alternative |

The optimistic-lock SQL is implemented in `AccountMapper.updateAvailableBalanceWithVersion`:

```sql
UPDATE account
SET available_balance = ?, version = version + 1
WHERE id = ? AND version = ?;
```

An affected-row count of zero means another transaction changed the account after
it was read. The caller must reload, revalidate the balance, and retry a bounded
number of times; it must never silently report success.

## Important limitation

Consistent lock order reduces deadlocks but cannot mathematically eliminate every
deadlock involving other tables, indexes, or code paths. Production code should
recognize database deadlock errors and retry the complete transaction a small,
bounded number of times when doing so is safe and idempotent.

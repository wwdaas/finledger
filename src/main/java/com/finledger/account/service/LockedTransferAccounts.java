package com.finledger.account.service;

import com.finledger.account.entity.AccountEntity;

public record LockedTransferAccounts(
        AccountEntity fromAccount,
        AccountEntity toAccount
) {
}

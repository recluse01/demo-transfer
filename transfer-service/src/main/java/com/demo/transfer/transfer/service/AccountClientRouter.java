package com.demo.transfer.transfer.service;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.client.AccountOperationsClient;
import org.springframework.stereotype.Component;

@Component
public class AccountClientRouter {
    private final AccountAClient accountAClient;
    private final AccountBClient accountBClient;

    public AccountClientRouter(AccountAClient accountAClient, AccountBClient accountBClient) {
        this.accountAClient = accountAClient;
        this.accountBClient = accountBClient;
    }

    public AccountType sourceType(TransferDirection direction) {
        if (TransferDirection.A_TO_B == direction) {
            return AccountType.ACCOUNT_A;
        }
        return AccountType.ACCOUNT_B;
    }

    public AccountType targetType(TransferDirection direction) {
        if (TransferDirection.A_TO_B == direction) {
            return AccountType.ACCOUNT_B;
        }
        return AccountType.ACCOUNT_A;
    }

    public AccountOperationsClient client(AccountType accountType) {
        if (AccountType.ACCOUNT_A == accountType) {
            return accountAClient;
        }
        return accountBClient;
    }
}

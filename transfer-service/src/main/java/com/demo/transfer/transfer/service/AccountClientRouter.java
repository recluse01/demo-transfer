package com.demo.transfer.transfer.service;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.client.AccountOperationsClient;
import org.springframework.stereotype.Component;

/**
 * 账户客户端路由器。
 *
 * <p>负责在转账方向、账户类型和具体客户端实现之间做映射。
 */
@Component
public class AccountClientRouter {
    /** A 账户服务客户端。 */
    private final AccountAClient accountAClient;
    /** B 账户服务客户端。 */
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

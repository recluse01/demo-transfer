package com.demo.transfer.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AccountClientRouter} 纯单元测试：覆盖方向→账户类型、账户类型→客户端的全部分支。
 */
@ExtendWith(MockitoExtension.class)
class AccountClientRouterTest {

    @Mock
    private AccountAClient accountAClient;

    @Mock
    private AccountBClient accountBClient;

    private AccountClientRouter router() {
        return new AccountClientRouter(accountAClient, accountBClient);
    }

    @Test
    void sourceTypeMapsByDirection() {
        AccountClientRouter router = router();
        assertThat(router.sourceType(TransferDirection.A_TO_B)).isEqualTo(AccountType.ACCOUNT_A);
        assertThat(router.sourceType(TransferDirection.B_TO_A)).isEqualTo(AccountType.ACCOUNT_B);
    }

    @Test
    void targetTypeMapsByDirection() {
        AccountClientRouter router = router();
        assertThat(router.targetType(TransferDirection.A_TO_B)).isEqualTo(AccountType.ACCOUNT_B);
        assertThat(router.targetType(TransferDirection.B_TO_A)).isEqualTo(AccountType.ACCOUNT_A);
    }

    @Test
    void clientMapsByAccountType() {
        AccountClientRouter router = router();
        assertThat(router.client(AccountType.ACCOUNT_A)).isSameAs(accountAClient);
        assertThat(router.client(AccountType.ACCOUNT_B)).isSameAs(accountBClient);
    }
}

package com.demo.transfer.transfer.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.service.TransferRetryService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TransferRetryScheduler} 纯单元测试。
 *
 * <p>直接调用调度方法（不依赖 {@code @Scheduled} 真实间隔），验证：
 * 仅扫描三种失败状态、逐单触发重试、单笔重试异常不影响其余单。
 */
@ExtendWith(MockitoExtension.class)
class TransferRetrySchedulerTest {

    @Mock
    private TransferOrderRepository orderRepository;

    @Mock
    private TransferRetryService retryService;

    @InjectMocks
    private TransferRetryScheduler scheduler;

    private TransferOrder order(String transferId, TransferStatus status) {
        TransferOrder o = TransferOrder.create(transferId, "user-1", AccountType.ACCOUNT_A, AccountType.ACCOUNT_B,
                "USDT", new BigDecimal("10.00000000"), TransferMode.MANUAL_REVIEW);
        o.markStatus(status);
        return o;
    }

    @Test
    void scansFailedStatusesAndRetriesEachOrder() {
        given(orderRepository.findTop100ByStatusInOrderByUpdatedAtAsc(anyList()))
                .willReturn(Arrays.asList(order("t-1", TransferStatus.DEBIT_FAILED),
                        order("t-2", TransferStatus.CREDIT_FAILED)));

        scheduler.retryFailedSteps();

        // 仅扫描 DEBIT_FAILED / CREDIT_FAILED / CANCEL_FAILED 三种可重试失败态
        ArgumentCaptor<Collection<TransferStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).findTop100ByStatusInOrderByUpdatedAtAsc(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                TransferStatus.DEBIT_FAILED, TransferStatus.CREDIT_FAILED, TransferStatus.CANCEL_FAILED);

        verify(retryService).retryOne("t-1");
        verify(retryService).retryOne("t-2");
    }

    @Test
    void continuesWhenOneRetryThrows() {
        given(orderRepository.findTop100ByStatusInOrderByUpdatedAtAsc(anyList()))
                .willReturn(Arrays.asList(order("t-1", TransferStatus.DEBIT_FAILED),
                        order("t-2", TransferStatus.CREDIT_FAILED)));
        given(retryService.retryOne("t-1")).willThrow(new IllegalStateException("boom"));

        scheduler.retryFailedSteps();

        // 第一笔抛异常被吞掉，第二笔仍被重试
        verify(retryService).retryOne("t-1");
        verify(retryService).retryOne("t-2");
    }

    @Test
    void doesNothingWhenNoFailedOrders() {
        given(orderRepository.findTop100ByStatusInOrderByUpdatedAtAsc(anyList()))
                .willReturn(Collections.emptyList());

        scheduler.retryFailedSteps();

        verify(retryService, org.mockito.Mockito.never()).retryOne(org.mockito.ArgumentMatchers.anyString());
    }
}

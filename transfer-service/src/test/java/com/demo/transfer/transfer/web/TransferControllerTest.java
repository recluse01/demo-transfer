package com.demo.transfer.transfer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.service.AccountClientRouter;
import com.demo.transfer.transfer.service.TransferOrderStateService;
import com.demo.transfer.transfer.workflow.TransferActivities;
import com.demo.transfer.transfer.workflow.TransferWorkflow;
import com.demo.transfer.transfer.workflow.TransferWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ContextConfiguration(classes = TransferControllerTest.ControllerConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransferControllerTest {
    private static final String TASK_QUEUE = "test-transfer-controller-queue";

    @Autowired
    private TransferController controller;

    @Autowired
    private TransferOrderRepository orderRepository;

    @Autowired
    private TransferOrderStateService stateService;

    @Autowired
    private AccountClientRouter router;

    @MockBean
    private AccountAClient accountAClient;

    @MockBean
    private AccountBClient accountBClient;

    @MockBean
    private WorkflowClient workflowClient;

    @BeforeEach
    void cleanOrders() {
        orderRepository.deleteAll();
    }

    @Test
    void returnsFailureAndDoesNotCreateOrderWhenOrderCreationFailsBeforePersistence() {
        CreateTransferRequest request = request("110.00");
        TransferOrderStateService failingStateService = mock(TransferOrderStateService.class);
        when(failingStateService.createOrder(any())).thenThrow(new RuntimeException("模拟创建订单失败"));
        TransferController failingController = new TransferController(
                failingStateService, workflowClient, router, TASK_QUEUE);

        ApiResponse<TransferOrder> response = failingController.create(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("TRANSFER_OPERATION_FAILED");
        assertThat(response.getMessage()).contains("模拟创建订单失败");
        assertThat(orderRepository.findAll()).isEmpty();
        verifyNoInteractions(workflowClient);
    }

    @Test
    void marksOrderInitFailedWhenWorkflowStartFailsAfterOrderCreated() {
        CreateTransferRequest request = request("90.00");
        when(workflowClient.newWorkflowStub(eq(TransferWorkflow.class), any(WorkflowOptions.class)))
                .thenThrow(new RuntimeException("workflow发送失败"));

        ApiResponse<TransferOrder> response = controller.create(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("TRANSFER_OPERATION_FAILED");
        assertThat(response.getMessage()).contains("Workflow 启动失败");
        TransferOrder order = orderRepository.findAll().get(0);
        assertThat(order.getStatus()).isEqualTo(TransferStatus.INIT_FAILED);
        assertThat(order.getLastErrorCode()).isEqualTo("WORKFLOW_START_FAILED");
        assertThat(order.getLastErrorMessage()).contains("workflow发送失败");
    }

    @Test
    void returnsSuccessWhenWorkflowStartSucceeds() {
        TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
        worker.registerActivitiesImplementations(new NoopActivities());
        testEnv.start();
        try {
            TransferController realWorkflowController = new TransferController(
                    stateService, testEnv.getWorkflowClient(), router, TASK_QUEUE);

            ApiResponse<TransferOrder> response = realWorkflowController.create(request("91.00"));

            assertThat(response.isSuccess()).isTrue();
            TransferOrder order = orderRepository.findAll().get(0);
            assertThat(order.getStatus()).isEqualTo(TransferStatus.CREATED);
            assertThat(order.getLastErrorCode()).isNull();
            assertThat(order.getLastErrorMessage()).isNull();
        } finally {
            testEnv.close();
        }
    }

    private CreateTransferRequest request(String amount) {
        return new CreateTransferRequest("user-" + UUID.randomUUID(), "USDT", new BigDecimal(amount),
                TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW);
    }

    static class NoopActivities implements TransferActivities {
        @Override
        public void freeze(String transferId) {
        }

        @Override
        public void confirmDebit(String transferId) {
        }

        @Override
        public void credit(String transferId) {
        }

        @Override
        public void cancelFreeze(String transferId) {
        }
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Import({TransferController.class, TransferOrderStateService.class, AccountClientRouter.class})
    static class ControllerConfig {
    }
}

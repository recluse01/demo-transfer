//package com.demo.transfer.transfer.integration;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.when;
//
//import com.demo.transfer.common.AccountType;
//import com.demo.transfer.common.ApiResponse;
//import com.demo.transfer.common.AssetOperationResponse;
//import com.demo.transfer.common.OperationType;
//import com.demo.transfer.common.TransferMode;
//import com.demo.transfer.common.TransferStatus;
//import com.demo.transfer.transfer.client.AccountAClient;
//import com.demo.transfer.transfer.client.AccountBClient;
//import com.demo.transfer.transfer.domain.TransferOrder;
//import com.demo.transfer.transfer.repository.TransferOrderRepository;
//import com.demo.transfer.transfer.service.AccountClientRouter;
//import com.demo.transfer.transfer.service.TransferOrderStateService;
//import com.demo.transfer.transfer.workflow.ReviewDecision;
//import com.demo.transfer.transfer.workflow.TransferActivitiesImpl;
//import com.demo.transfer.transfer.workflow.TransferWorkflow;
//import com.demo.transfer.transfer.workflow.TransferWorkflowImpl;
//import io.temporal.client.WorkflowClient;
//import io.temporal.client.WorkflowOptions;
//import io.temporal.testing.TestWorkflowEnvironment;
//import io.temporal.worker.Worker;
//import java.math.BigDecimal;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.domain.EntityScan;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
//import org.springframework.test.context.ContextConfiguration;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
///**
// * 端到端场景集成测试。
// *
// * <p>使用 TestWorkflowEnvironment + 真实 TransferActivitiesImpl + H2 内存库。
// * 禁用 Spring Test 的自动事务包裹（NOT_SUPPORTED），确保测试写入的订单数据能被
// * Temporal Worker 线程（另一事务）立即读到。
// */
//@DataJpaTest
//@ContextConfiguration(classes = TransferScenarioIntegrationTest.ScenarioConfig.class)
//@Transactional(propagation = Propagation.NOT_SUPPORTED)
//class TransferScenarioIntegrationTest {
//    private static final String TASK_QUEUE = "integration-test-queue";
//
//    @Autowired
//    private TransferActivitiesImpl activities;
//
//    @Autowired
//    private TransferOrderStateService stateService;
//
//    @Autowired
//    private TransferOrderRepository orderRepository;
//
//    @MockBean
//    private AccountAClient accountAClient;
//
//    @MockBean
//    private AccountBClient accountBClient;
//
//    private TestWorkflowEnvironment testEnv;
//
//    @BeforeEach
//    void setUp() {
//        when(accountAClient.freeze(any())).thenReturn(ok(OperationType.FREEZE));
//        when(accountBClient.freeze(any())).thenReturn(ok(OperationType.FREEZE));
//        when(accountAClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
//        when(accountBClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
//        when(accountAClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
//        when(accountBClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
//        when(accountAClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
//        when(accountBClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
//
//        testEnv = TestWorkflowEnvironment.newInstance();
//        Worker worker = testEnv.newWorker(TASK_QUEUE);
//        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
//        worker.registerActivitiesImplementations(activities);
//        testEnv.start();
//    }
//
//    @AfterEach
//    void tearDown() {
//        testEnv.close();
//        orderRepository.deleteAll();
//    }
//
//    @Test
//    void scenarioOneApprovalCompletesAToBManualTransfer() {
//        String transferId = "txn-scenario-1";
//        createOrder(transferId, AccountType.ACCOUNT_A, AccountType.ACCOUNT_B, TransferMode.MANUAL_REVIEW);
//
//        TransferWorkflow workflow = workflow(transferId, TransferMode.MANUAL_REVIEW);
//        WorkflowClient.start(workflow::execute, transferId, TransferMode.MANUAL_REVIEW);
//        workflow.review(new ReviewDecision(true, "approved"));
//        awaitResult(transferId);
//
//        assertThat(reload(transferId).getStatus()).isEqualTo(TransferStatus.SUCCESS);
//    }
//
//    @Test
//    void scenarioOneRejectionCancelsAToBManualTransfer() {
//        String transferId = "txn-scenario-2";
//        createOrder(transferId, AccountType.ACCOUNT_A, AccountType.ACCOUNT_B, TransferMode.MANUAL_REVIEW);
//
//        TransferWorkflow workflow = workflow(transferId, TransferMode.MANUAL_REVIEW);
//        WorkflowClient.start(workflow::execute, transferId, TransferMode.MANUAL_REVIEW);
//        workflow.review(new ReviewDecision(false, "rejected"));
//        awaitResult(transferId);
//
//        assertThat(reload(transferId).getStatus()).isEqualTo(TransferStatus.REJECTED);
//    }
//
//    @Test
//    void scenarioTwoApprovalCompletesBToAManualTransfer() {
//        String transferId = "txn-scenario-3";
//        createOrder(transferId, AccountType.ACCOUNT_B, AccountType.ACCOUNT_A, TransferMode.MANUAL_REVIEW);
//
//        TransferWorkflow workflow = workflow(transferId, TransferMode.MANUAL_REVIEW);
//        WorkflowClient.start(workflow::execute, transferId, TransferMode.MANUAL_REVIEW);
//        workflow.review(new ReviewDecision(true, "approved"));
//        awaitResult(transferId);
//
//        assertThat(reload(transferId).getStatus()).isEqualTo(TransferStatus.SUCCESS);
//    }
//
//    @Test
//    void scenarioThreeAutoWithdrawAToBCompletesWithoutReview() {
//        String transferId = "txn-scenario-4";
//        createOrder(transferId, AccountType.ACCOUNT_A, AccountType.ACCOUNT_B, TransferMode.AUTO_WITHDRAW);
//
//        workflow(transferId, TransferMode.AUTO_WITHDRAW).execute(transferId, TransferMode.AUTO_WITHDRAW);
//
//        assertThat(reload(transferId).getStatus()).isEqualTo(TransferStatus.SUCCESS);
//    }
//
//    @Test
//    void scenarioThreeAutoWithdrawBToACompletesWithoutReview() {
//        String transferId = "txn-scenario-5";
//        createOrder(transferId, AccountType.ACCOUNT_B, AccountType.ACCOUNT_A, TransferMode.AUTO_WITHDRAW);
//
//        workflow(transferId, TransferMode.AUTO_WITHDRAW).execute(transferId, TransferMode.AUTO_WITHDRAW);
//
//        assertThat(reload(transferId).getStatus()).isEqualTo(TransferStatus.SUCCESS);
//    }
//
//    @Test
//    void autoWithdrawCreditFailureLeavesCreditFailed() {
//        when(accountBClient.credit(any()))
//                .thenReturn(ApiResponse.fail("TIMEOUT", "credit timeout"));
//        String transferId = "txn-scenario-6";
//        createOrder(transferId, AccountType.ACCOUNT_A, AccountType.ACCOUNT_B, TransferMode.AUTO_WITHDRAW);
//
//        try {
//            workflow(transferId, TransferMode.AUTO_WITHDRAW).execute(transferId, TransferMode.AUTO_WITHDRAW);
//        } catch (Exception ignored) {
//            // WorkflowException after all retries exhausted
//        }
//
//        assertThat(reload(transferId).getStatus()).isEqualTo(TransferStatus.CREDIT_FAILED);
//    }
//
//    private void createOrder(String transferId, AccountType source, AccountType target, TransferMode mode) {
//        TransferOrder order = TransferOrder.create(transferId, "user-1", source, target,
//                "USDT", new BigDecimal("10.00"), mode);
//        stateService.createOrder(order);
//    }
//
//    private TransferWorkflow workflow(String transferId, TransferMode mode) {
//        return testEnv.getWorkflowClient().newWorkflowStub(
//                TransferWorkflow.class,
//                WorkflowOptions.newBuilder()
//                        .setWorkflowId(transferId)
//                        .setTaskQueue(TASK_QUEUE)
//                        .build());
//    }
//
//    private void awaitResult(String transferId) {
//        testEnv.getWorkflowClient().newUntypedWorkflowStub(transferId).getResult(Void.class);
//    }
//
//    private TransferOrder reload(String transferId) {
//        return orderRepository.findByTransferId(transferId).orElseThrow();
//    }
//
//    private ApiResponse<AssetOperationResponse> ok(OperationType type) {
//        return ApiResponse.ok(new AssetOperationResponse("transfer", type, true, "success"));
//    }
//
//    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
//    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
//    @Import({TransferActivitiesImpl.class, TransferOrderStateService.class, AccountClientRouter.class})
//    static class ScenarioConfig {
//    }
//}

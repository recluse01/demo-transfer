package com.demo.transfer.transfer.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.transfer.common.TransferMode;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferWorkflowImplTest {
    private static final String TASK_QUEUE = "test-transfer-queue";
    private static final String TRANSFER_ID = "txn-test-001";

    private TestWorkflowEnvironment testEnv;
    private StubActivities activities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);

        activities = new StubActivities();
        worker.registerActivitiesImplementations(activities);
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void autoWithdrawRunsFullSequence() {
        execute(TransferMode.AUTO_WITHDRAW);

        assertThat(activities.calls).containsExactly("freeze", "confirmDebit", "credit");
    }

    @Test
    void manualReviewApprovedRunsFullSequence() {
        TransferWorkflow workflow = stub();
        WorkflowClient.start(workflow::execute, TRANSFER_ID, TransferMode.MANUAL_REVIEW);

        workflow.review(new ReviewDecision(true, "approved"));
        testEnv.getWorkflowClient().newUntypedWorkflowStub(TRANSFER_ID).getResult(Void.class);

        assertThat(activities.calls).containsExactly("freeze", "confirmDebit", "credit");
    }

    @Test
    void manualReviewRejectedCancelsFreeze() {
        TransferWorkflow workflow = stub();
        WorkflowClient.start(workflow::execute, TRANSFER_ID, TransferMode.MANUAL_REVIEW);

        workflow.review(new ReviewDecision(false, "rejected"));
        testEnv.getWorkflowClient().newUntypedWorkflowStub(TRANSFER_ID).getResult(Void.class);

        assertThat(activities.calls).containsExactly("freeze", "cancelFreeze");
    }

    @Test
    void activityFailureTriggersRetryUntilSuccess() {
        activities.freezeFailTimes = 1;

        execute(TransferMode.AUTO_WITHDRAW);

        // 共调用两次：第一次失败触发重试，第二次成功
        assertThat(activities.freezeAttempts).isEqualTo(2);
        assertThat(activities.calls).contains("freeze", "confirmDebit", "credit");
    }

    private void execute(TransferMode mode) {
        stub().execute(TRANSFER_ID, mode);
    }

    private TransferWorkflow stub() {
        return testEnv.getWorkflowClient().newWorkflowStub(
                TransferWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(TRANSFER_ID)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
    }

    /** 具体 Activities 实现，支持失败次数控制和调用记录。 */
    static class StubActivities implements TransferActivities {
        final List<String> calls = new ArrayList<>();
        int freezeFailTimes = 0;
        int freezeAttempts = 0;

        @Override
        public void freeze(String transferId) {
            freezeAttempts++;
            if (freezeAttempts <= freezeFailTimes) {
                throw new RuntimeException("transient error on attempt " + freezeAttempts);
            }
            calls.add("freeze");
        }

        @Override
        public void confirmDebit(String transferId) {
            calls.add("confirmDebit");
        }

        @Override
        public void credit(String transferId) {
            calls.add("credit");
        }

        @Override
        public void cancelFreeze(String transferId) {
            calls.add("cancelFreeze");
        }
    }
}

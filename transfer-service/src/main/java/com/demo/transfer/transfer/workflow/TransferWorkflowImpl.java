package com.demo.transfer.transfer.workflow;

import com.demo.transfer.common.TransferMode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * 转账 Workflow 实现。
 *
 * <p>由 Temporal Worker 实例化，不是 Spring Bean，因此不能使用 @Value 或 @Autowired。
 * ActivityOptions 在此处配置：startToCloseTimeout=30s，指数退避，最大 10 次重试。
 */
public class TransferWorkflowImpl implements TransferWorkflow {
    private static final Logger log = Workflow.getLogger(TransferWorkflowImpl.class);

    private final TransferActivities activities = Workflow.newActivityStub(
            TransferActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofMinutes(5))
                            .setMaximumAttempts(10)
                            .build())
                    .build());

    private ReviewDecision reviewDecision;

    @Override
    public void execute(String transferId, TransferMode transferMode) {
        log.info("Transfer workflow started, transferId={}, mode={}", transferId, transferMode);
        activities.freeze(transferId);

        if (TransferMode.MANUAL_REVIEW == transferMode) {
            log.info("Transfer workflow waiting for review, transferId={}", transferId);
            Workflow.await(() -> reviewDecision != null);
            if (!reviewDecision.isApproved()) {
                log.info("Transfer workflow review rejected, transferId={}, message={}",
                        transferId, reviewDecision.getMessage());
                activities.cancelFreeze(transferId);
                return;
            }
            log.info("Transfer workflow review approved, transferId={}, message={}",
                    transferId, reviewDecision.getMessage());
        }

        activities.confirmDebit(transferId);
        activities.credit(transferId);
        log.info("Transfer workflow completed, transferId={}", transferId);
    }

    @Override
    public void review(ReviewDecision decision) {
        log.info("Transfer workflow received review signal, approved={}, message={}",
                decision.isApproved(), decision.getMessage());
        this.reviewDecision = decision;
    }
}

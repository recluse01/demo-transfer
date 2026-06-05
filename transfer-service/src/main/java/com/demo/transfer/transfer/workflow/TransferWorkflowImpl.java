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
        log.info("转账 Workflow 开始执行，transferId={}, mode={}", transferId, transferMode);
        activities.freeze(transferId);

        if (TransferMode.MANUAL_REVIEW == transferMode) {
            log.info("转账 Workflow 等待人工审核，transferId={}", transferId);
            Workflow.await(() -> reviewDecision != null);
            if (!reviewDecision.isApproved()) {
                log.info("转账 Workflow 审核已拒绝，transferId={}, message={}",
                        transferId, reviewDecision.getMessage());
                activities.cancelFreeze(transferId);
                return;
            }
            log.info("转账 Workflow 审核已通过，transferId={}, message={}",
                    transferId, reviewDecision.getMessage());
        }

        activities.confirmDebit(transferId);
        activities.credit(transferId);
        log.info("转账 Workflow 执行完成，transferId={}", transferId);
    }

    @Override
    public void review(ReviewDecision decision) {
        log.info("转账 Workflow 收到审核 Signal，approved={}, message={}",
                decision.isApproved(), decision.getMessage());
        this.reviewDecision = decision;
    }
}

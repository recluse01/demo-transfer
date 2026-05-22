package com.demo.transfer.transfer.workflow;

import com.demo.transfer.common.TransferMode;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 转账 Workflow 接口。
 *
 * <p>以 transferId 作为 WorkflowId，保证全局唯一且便于查询和发送 Signal。
 */
@WorkflowInterface
public interface TransferWorkflow {
    @WorkflowMethod
    void execute(String transferId, TransferMode transferMode);

    @SignalMethod
    void review(ReviewDecision decision);
}

package com.demo.transfer.transfer.workflow;

/** 人工审核结果，通过 Signal 发送给 TransferWorkflow。 */
public class ReviewDecision {
    private boolean approved;
    private String message;

    public ReviewDecision() {
    }

    public ReviewDecision(boolean approved, String message) {
        this.approved = approved;
        this.message = message;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getMessage() {
        return message;
    }
}

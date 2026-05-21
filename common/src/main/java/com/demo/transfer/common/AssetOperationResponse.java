package com.demo.transfer.common;

public class AssetOperationResponse {
    private String transferId;
    private OperationType operationType;
    private boolean applied;
    private String message;

    public AssetOperationResponse() {
    }

    public AssetOperationResponse(String transferId, OperationType operationType, boolean applied, String message) {
        this.transferId = transferId;
        this.operationType = operationType;
        this.applied = applied;
        this.message = message;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

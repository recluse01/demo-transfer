package com.demo.transfer.common;

/**
 * 账户资产操作响应。
 *
 * <p>applied=true 表示本次请求实际执行了余额变更；
 * applied=false 表示命中了幂等记录，没有重复执行。
 */
public class AssetOperationResponse {
    /** 业务转账号。 */
    private String transferId;
    /** 本次返回对应的资产操作类型。 */
    private OperationType operationType;
    /** 是否真正执行了本次变更。 */
    private boolean applied;
    /** 处理结果说明。 */
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

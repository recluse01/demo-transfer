package com.demo.transfer.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 账户资产操作响应。
 *
 * <p>applied=true 表示本次请求实际执行了余额变更；
 * applied=false 表示命中了幂等记录，没有重复执行。
 */
@Schema(description = "账户资产操作响应")
public class AssetOperationResponse {
    /** 业务转账号。 */
    @Schema(description = "业务转账 ID", example = "debug-credit-001")
    private String transferId;
    /** 本次返回对应的资产操作类型。 */
    @Schema(description = "资产操作类型", example = "CREDIT")
    private OperationType operationType;
    /** 是否真正执行了本次变更。 */
    @Schema(description = "是否实际执行本次变更", example = "true")
    private boolean applied;
    /** 处理结果说明。 */
    @Schema(description = "处理结果说明", example = "CREDIT success")
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

package com.demo.transfer.common;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class AssetOperationRequest {
    @NotBlank
    private String transferId;

    @NotBlank
    private String userId;

    @NotBlank
    private String assetCode;

    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal amount;

    @NotNull
    private TransferDirection direction;

    public AssetOperationRequest() {
    }

    public AssetOperationRequest(String transferId, String userId, String assetCode, BigDecimal amount,
            TransferDirection direction) {
        this.transferId = transferId;
        this.userId = userId;
        this.assetCode = assetCode;
        this.amount = amount;
        this.direction = direction;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public void setDirection(TransferDirection direction) {
        this.direction = direction;
    }
}

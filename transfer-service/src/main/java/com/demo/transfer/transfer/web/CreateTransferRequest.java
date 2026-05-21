package com.demo.transfer.transfer.web;

import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 创建转账请求。 */
public class CreateTransferRequest {
    /** 发起转账的用户。 */
    @NotBlank
    private String userId;

    /** 转账资产编码。 */
    @NotBlank
    private String assetCode;

    /** 转账金额。 */
    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal amount;

    /** 转账方向。 */
    @NotNull
    private TransferDirection direction;

    /** 转账模式。 */
    @NotNull
    private TransferMode mode;

    public CreateTransferRequest() {
    }

    public CreateTransferRequest(String userId, String assetCode, BigDecimal amount, TransferDirection direction,
            TransferMode mode) {
        this.userId = userId;
        this.assetCode = assetCode;
        this.amount = amount;
        this.direction = direction;
        this.mode = mode;
    }

    public String getUserId() {
        return userId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public TransferMode getMode() {
        return mode;
    }
}

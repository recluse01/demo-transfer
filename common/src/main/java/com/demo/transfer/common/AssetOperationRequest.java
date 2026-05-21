package com.demo.transfer.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 账户资产操作请求。
 *
 * <p>由转账服务发给账户服务，描述某笔转账在账户侧需要执行的单个动作。
 */
@Schema(description = "账户资产操作请求")
public class AssetOperationRequest {
    /** 业务转账号，也是账户侧幂等主键的一部分。 */
    @Schema(description = "业务转账 ID", example = "debug-freeze-001")
    @NotBlank
    private String transferId;

    /** 用户标识。 */
    @Schema(description = "用户 ID", example = "user-1")
    @NotBlank
    private String userId;

    /** 资产编码。 */
    @Schema(description = "资产编码", example = "USDT")
    @NotBlank
    private String assetCode;

    /** 本次操作金额，最小精度为 0.00000001。 */
    @Schema(description = "操作金额", example = "10")
    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal amount;

    /** 转账方向，便于账户服务按上下文做审计或分流。 */
    @Schema(description = "转账方向", allowableValues = {"A_TO_B", "B_TO_A"}, example = "A_TO_B")
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

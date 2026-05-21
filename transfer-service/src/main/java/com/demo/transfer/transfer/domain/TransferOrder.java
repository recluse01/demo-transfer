package com.demo.transfer.transfer.domain;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

@Entity
@Table(name = "transfer_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_order_transfer_id", columnNames = "transfer_id")
})
/**
 * 转账主单实体。
 *
 * <p>记录一笔跨账户转账在 Saga 流程中的当前状态、账户方向、金额和最近一次失败信息。
 */
public class TransferOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务侧转账唯一标识，贯穿整个 Saga 链路。 */
    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    /** 用户唯一标识。 */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 源账户类型，表示资产扣减的起点。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_account_type", nullable = false, length = 32)
    private AccountType sourceAccountType;

    /** 目标账户类型，表示资产入账的终点。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_account_type", nullable = false, length = 32)
    private AccountType targetAccountType;

    /** 资产编码，例如币种或产品代码。 */
    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    /** 转账金额。 */
    @Column(name = "amount", nullable = false, precision = 32, scale = 8)
    private BigDecimal amount;

    /** 转账处理模式，决定是人工审核还是自动提现。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_mode", nullable = false, length = 32)
    private TransferMode transferMode;

    /** 当前 Saga 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransferStatus status;

    /** 最近一次失败的错误码。 */
    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    /** 最近一次失败的错误信息。 */
    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    /** 乐观锁版本号，用于防止并发覆盖。 */
    @Version
    private Long version;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 创建新的转账主单，初始状态固定为 {@link TransferStatus#CREATED}。 */
    public static TransferOrder create(String transferId, String userId, AccountType sourceAccountType,
            AccountType targetAccountType, String assetCode, BigDecimal amount, TransferMode transferMode) {
        TransferOrder order = new TransferOrder();
        order.transferId = transferId;
        order.userId = userId;
        order.sourceAccountType = sourceAccountType;
        order.targetAccountType = targetAccountType;
        order.assetCode = assetCode;
        order.amount = amount;
        order.transferMode = transferMode;
        order.status = TransferStatus.CREATED;
        return order;
    }

    /** 标记当前流程进入新的成功中间状态，同时清空历史错误信息。 */
    public void markStatus(TransferStatus status) {
        this.status = status;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /** 标记当前流程失败，并保留本次失败上下文。 */
    public void markFailure(TransferStatus status, String code, String message) {
        this.status = status;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getUserId() {
        return userId;
    }

    public AccountType getSourceAccountType() {
        return sourceAccountType;
    }

    public AccountType getTargetAccountType() {
        return targetAccountType;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferMode getTransferMode() {
        return transferMode;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}

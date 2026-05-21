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
public class TransferOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_account_type", nullable = false, length = 32)
    private AccountType sourceAccountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_account_type", nullable = false, length = 32)
    private AccountType targetAccountType;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Column(name = "amount", nullable = false, precision = 32, scale = 8)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_mode", nullable = false, length = 32)
    private TransferMode transferMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransferStatus status;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public void markStatus(TransferStatus status) {
        this.status = status;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

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

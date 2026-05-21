package com.demo.transfer.account.domain;

import com.demo.transfer.common.OperationType;
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

@Entity
@Table(name = "asset_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asset_operation_transfer_type", columnNames = {"transfer_id", "operation_type"})
})
public class AssetOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Column(name = "amount", nullable = false, precision = 32, scale = 8)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OperationStatus status;

    @Column(name = "response_code", nullable = false, length = 32)
    private String responseCode;

    @Column(name = "response_message", nullable = false, length = 255)
    private String responseMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AssetOperation success(String transferId, OperationType operationType, String userId, String assetCode,
            BigDecimal amount, String message) {
        AssetOperation operation = new AssetOperation();
        operation.transferId = transferId;
        operation.operationType = operationType;
        operation.userId = userId;
        operation.assetCode = assetCode;
        operation.amount = amount;
        operation.status = OperationStatus.SUCCESS;
        operation.responseCode = "OK";
        operation.responseMessage = message;
        return operation;
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

    public OperationType getOperationType() {
        return operationType;
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

    public OperationStatus getStatus() {
        return status;
    }

    public String getResponseMessage() {
        return responseMessage;
    }
}

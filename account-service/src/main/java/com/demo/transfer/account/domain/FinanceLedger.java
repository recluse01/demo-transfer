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
import javax.persistence.Table;

@Entity
@Table(name = "finance_ledger")
public class FinanceLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    @Column(name = "available_delta", nullable = false, precision = 32, scale = 8)
    private BigDecimal availableDelta;

    @Column(name = "frozen_delta", nullable = false, precision = 32, scale = 8)
    private BigDecimal frozenDelta;

    @Column(name = "available_after", nullable = false, precision = 32, scale = 8)
    private BigDecimal availableAfter;

    @Column(name = "frozen_after", nullable = false, precision = 32, scale = 8)
    private BigDecimal frozenAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static FinanceLedger of(String transferId, String userId, String assetCode, OperationType operationType,
            BigDecimal availableDelta, BigDecimal frozenDelta, BigDecimal availableAfter, BigDecimal frozenAfter) {
        FinanceLedger ledger = new FinanceLedger();
        ledger.transferId = transferId;
        ledger.userId = userId;
        ledger.assetCode = assetCode;
        ledger.operationType = operationType;
        ledger.availableDelta = availableDelta;
        ledger.frozenDelta = frozenDelta;
        ledger.availableAfter = availableAfter;
        ledger.frozenAfter = frozenAfter;
        return ledger;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

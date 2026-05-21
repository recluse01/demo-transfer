package com.demo.transfer.account.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

@Entity
@Table(name = "account_balance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_balance_user_asset", columnNames = {"user_id", "asset_code"})
})
public class AccountBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "asset_code", nullable = false, length = 32)
    private String assetCode;

    @Column(name = "available_amount", nullable = false, precision = 32, scale = 8)
    private BigDecimal availableAmount = BigDecimal.ZERO;

    @Column(name = "frozen_amount", nullable = false, precision = 32, scale = 8)
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AccountBalance create(String userId, String assetCode, BigDecimal availableAmount) {
        AccountBalance balance = new AccountBalance();
        balance.userId = userId;
        balance.assetCode = assetCode;
        balance.availableAmount = availableAmount;
        balance.frozenAmount = BigDecimal.ZERO;
        return balance;
    }

    public void freeze(BigDecimal amount) {
        availableAmount = availableAmount.subtract(amount);
        frozenAmount = frozenAmount.add(amount);
    }

    public void confirmDebit(BigDecimal amount) {
        frozenAmount = frozenAmount.subtract(amount);
    }

    public void cancelFreeze(BigDecimal amount) {
        availableAmount = availableAmount.add(amount);
        frozenAmount = frozenAmount.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        availableAmount = availableAmount.add(amount);
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

    public String getUserId() {
        return userId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public BigDecimal getFrozenAmount() {
        return frozenAmount;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

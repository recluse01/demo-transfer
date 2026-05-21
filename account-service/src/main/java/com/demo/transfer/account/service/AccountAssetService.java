package com.demo.transfer.account.service;

import com.demo.transfer.account.domain.AccountBalance;
import com.demo.transfer.account.domain.AssetOperation;
import com.demo.transfer.account.domain.FinanceLedger;
import com.demo.transfer.account.repository.AccountBalanceRepository;
import com.demo.transfer.account.repository.AssetOperationRepository;
import com.demo.transfer.account.repository.FinanceLedgerRepository;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAssetService {
    private final AccountBalanceRepository balanceRepository;
    private final AssetOperationRepository operationRepository;
    private final FinanceLedgerRepository ledgerRepository;

    public AccountAssetService(AccountBalanceRepository balanceRepository, AssetOperationRepository operationRepository,
            FinanceLedgerRepository ledgerRepository) {
        this.balanceRepository = balanceRepository;
        this.operationRepository = operationRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public AssetOperationResponse freeze(AssetOperationRequest request) {
        return apply(request, OperationType.FREEZE);
    }

    @Transactional
    public AssetOperationResponse confirmDebit(AssetOperationRequest request) {
        return apply(request, OperationType.CONFIRM_DEBIT);
    }

    @Transactional
    public AssetOperationResponse cancelFreeze(AssetOperationRequest request) {
        return apply(request, OperationType.CANCEL_FREEZE);
    }

    @Transactional
    public AssetOperationResponse credit(AssetOperationRequest request) {
        return apply(request, OperationType.CREDIT);
    }

    private AssetOperationResponse apply(AssetOperationRequest request, OperationType operationType) {
        AssetOperation existing = operationRepository
                .findByTransferIdAndOperationType(request.getTransferId(), operationType)
                .orElse(null);
        if (existing != null) {
            return new AssetOperationResponse(request.getTransferId(), operationType, false,
                    existing.getResponseMessage());
        }

        AccountBalance balance = balanceRepository
                .findByUserIdAndAssetCodeForUpdate(request.getUserId(), request.getAssetCode())
                .orElseThrow(() -> new IllegalStateException("account balance not found"));

        BalanceDelta delta = mutate(balance, operationType, request.getAmount());
        ledgerRepository.save(FinanceLedger.of(request.getTransferId(), request.getUserId(), request.getAssetCode(),
                operationType, delta.availableDelta, delta.frozenDelta, balance.getAvailableAmount(),
                balance.getFrozenAmount()));
        operationRepository.save(AssetOperation.success(request.getTransferId(), operationType, request.getUserId(),
                request.getAssetCode(), request.getAmount(), operationType.name() + " success"));
        return new AssetOperationResponse(request.getTransferId(), operationType, true, operationType.name() + " success");
    }

    private BalanceDelta mutate(AccountBalance balance, OperationType operationType, BigDecimal amount) {
        if (OperationType.FREEZE == operationType) {
            requireEnough(balance.getAvailableAmount(), amount, "insufficient available balance");
            balance.freeze(amount);
            return new BalanceDelta(amount.negate(), amount);
        }
        if (OperationType.CONFIRM_DEBIT == operationType) {
            requireEnough(balance.getFrozenAmount(), amount, "insufficient frozen balance");
            balance.confirmDebit(amount);
            return new BalanceDelta(BigDecimal.ZERO, amount.negate());
        }
        if (OperationType.CANCEL_FREEZE == operationType) {
            requireEnough(balance.getFrozenAmount(), amount, "insufficient frozen balance");
            balance.cancelFreeze(amount);
            return new BalanceDelta(amount, amount.negate());
        }
        balance.credit(amount);
        return new BalanceDelta(amount, BigDecimal.ZERO);
    }

    private void requireEnough(BigDecimal balance, BigDecimal amount, String message) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException(message);
        }
    }

    private static class BalanceDelta {
        private final BigDecimal availableDelta;
        private final BigDecimal frozenDelta;

        private BalanceDelta(BigDecimal availableDelta, BigDecimal frozenDelta) {
            this.availableDelta = availableDelta;
            this.frozenDelta = frozenDelta;
        }
    }
}

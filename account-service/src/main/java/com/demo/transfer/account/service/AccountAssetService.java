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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户资产操作服务。
 *
 * <p>负责执行冻结、确认扣减、取消冻结、入账四类原子操作，
 * 并同步落操作幂等记录和资金流水。
 */
@Slf4j
@Service
public class AccountAssetService {
    /** 账户余额仓储，提供行级锁读取能力。 */
    private final AccountBalanceRepository balanceRepository;
    /** 资产操作仓储，用于幂等去重。 */
    private final AssetOperationRepository operationRepository;
    /** 资金流水仓储，用于审计余额变化。 */
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

    /**
     * 执行单个资产操作。
     *
     * <p>处理顺序：
     * 1. 锁定余额记录（FOR UPDATE），将并发请求串行化。
     * 2. 在锁保护范围内做幂等检查，避免并发路径绕过幂等逻辑。
     * 3. 完成金额变更，写入资金流水和操作记录。
     */
    private AssetOperationResponse apply(AssetOperationRequest request, OperationType operationType) {
        log.info("开始执行账户资产操作，transferId={}, operationType={}, userId={}, assetCode={}, amount={}",
                request.getTransferId(), operationType, request.getUserId(), request.getAssetCode(),
                request.getAmount());
        AccountBalance balance = balanceRepository
                .findByUserIdAndAssetCodeForUpdate(request.getUserId(), request.getAssetCode())
                .orElseThrow(() -> new IllegalStateException("account balance not found"));

        // 加锁后再检查幂等：并发请求在此处串行化，第二个请求可见第一个已写入的幂等记录。
        AssetOperation existing = operationRepository
                .findByTransferIdAndOperationType(request.getTransferId(), operationType)
                .orElse(null);
        if (existing != null) {
            log.warn("账户资产操作已处理，命中幂等记录，transferId={}, operationType={}, message={}",
                    request.getTransferId(), operationType, existing.getResponseMessage());
            return new AssetOperationResponse(request.getTransferId(), operationType, false,
                    existing.getResponseMessage());
        }

        BalanceDelta delta = mutate(balance, operationType, request.getAmount());
        ledgerRepository.save(FinanceLedger.of(request.getTransferId(), request.getUserId(), request.getAssetCode(),
                operationType, delta.availableDelta, delta.frozenDelta, balance.getAvailableAmount(),
                balance.getFrozenAmount()));
        operationRepository.save(AssetOperation.success(request.getTransferId(), operationType, request.getUserId(),
                request.getAssetCode(), request.getAmount(), operationType.name() + " success"));
        log.info("账户资产操作执行完成，transferId={}, operationType={}, availableAmount={}, frozenAmount={}",
                request.getTransferId(), operationType, balance.getAvailableAmount(), balance.getFrozenAmount());
        return new AssetOperationResponse(request.getTransferId(), operationType, true, operationType.name() + " success");
    }

    /**
     * 根据操作类型修改余额。
     *
     * <p>availableDelta 和 frozenDelta 描述本次操作对两个余额桶的影响，用于记账。
     */
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
        // 余额校验统一放在服务层，避免领域对象静默失败。
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException(message);
        }
    }

    /** 单次资产操作对可用余额和冻结余额的变化量。 */
    private static class BalanceDelta {
        /** 可用余额变化量，负数表示扣减，正数表示增加。 */
        private final BigDecimal availableDelta;
        /** 冻结余额变化量，负数表示减少，正数表示增加。 */
        private final BigDecimal frozenDelta;

        private BalanceDelta(BigDecimal availableDelta, BigDecimal frozenDelta) {
            this.availableDelta = availableDelta;
            this.frozenDelta = frozenDelta;
        }
    }
}

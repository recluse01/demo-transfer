package com.demo.transfer.account.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.transfer.account.domain.AccountBalance;
import com.demo.transfer.account.domain.AssetOperation;
import com.demo.transfer.account.repository.AccountBalanceRepository;
import com.demo.transfer.account.repository.AssetOperationRepository;
import com.demo.transfer.account.repository.FinanceLedgerRepository;
import com.demo.transfer.account.service.AccountAssetService;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保真轨幂等性集成测试（4.1）。
 *
 * <p>在真实 MySQL 容器（DECIMAL(32,8)、真实唯一索引）上验证：
 * <ol>
 *   <li>相同 transferId + operationType 的重复调用返回 {@code applied=false}，
 *       余额与流水仅被应用一次。</li>
 *   <li>数据库唯一索引直接拒绝重复 {@link AssetOperation} 插入，
 *       抛出 {@link DataIntegrityViolationException}——H2 保真轨对应版本。</li>
 * </ol>
 *
 * <p>继承 {@link AbstractMySqlIntegrationTest} 以复用单例 MySQL 容器与连接属性注入。
 * 使用 {@code @AutoConfigureTestDatabase(replace = NONE)} 阻止 Spring 替换为 H2。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AccountAssetIdempotencyIT.JpaConfig.class)
class AccountAssetIdempotencyIT extends AbstractMySqlIntegrationTest {

    @Autowired
    private AccountAssetService service;

    @Autowired
    private AccountBalanceRepository balanceRepository;

    @Autowired
    private AssetOperationRepository operationRepository;

    @Autowired
    private FinanceLedgerRepository ledgerRepository;

    // -------------------------------------------------------------------------
    // 测试 1：服务层幂等——第二次调用返回 applied=false，余额与流水仅变更一次
    // -------------------------------------------------------------------------

    /**
     * 冻结操作：相同幂等键第二次调用返回 applied=false，余额不再变动。
     *
     * <p>种子数据（来自真实 DDL init 脚本）：user-1 / USDT 可用 1000.00000000。
     * 使用独立 transfer-id 避免与其他测试中种子数据冲突。
     */
    @Test
    @Transactional
    void duplicateFreezeReturnsAppliedFalseWithNoSideEffect() {
        // 准备：确保 user-1/USDT 可用余额足够（种子数据已有 1000，但需可读写，套在同一事务内）
        AccountBalance balance = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(() -> new AssertionError("种子余额不存在"));
        BigDecimal initialAvailable = balance.getAvailableAmount();
        BigDecimal initialFrozen = balance.getFrozenAmount();

        AssetOperationRequest req = buildRequest("it-idempotency-freeze-1", "10.00000001");

        // 第一次调用
        AssetOperationResponse first = service.freeze(req);
        assertThat(first.isApplied()).isTrue();

        // 第二次调用——相同 transferId + operationType
        AssetOperationResponse second = service.freeze(req);
        assertThat(second.isApplied()).isFalse();

        // 余额只变动一次：可用减少 10.00000001，冻结增加 10.00000001
        AccountBalance after = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(AssertionError::new);
        assertThat(after.getAvailableAmount())
                .isEqualByComparingTo(initialAvailable.subtract(new BigDecimal("10.00000001")));
        assertThat(after.getFrozenAmount())
                .isEqualByComparingTo(initialFrozen.add(new BigDecimal("10.00000001")));

        // 流水仅写入一条
        assertThat(ledgerRepository.countByTransferIdAndOperationType("it-idempotency-freeze-1", OperationType.FREEZE))
                .isEqualTo(1L);

        // 幂等记录仅一条
        assertThat(operationRepository.findByTransferIdAndOperationType("it-idempotency-freeze-1", OperationType.FREEZE))
                .isPresent();
    }

    /**
     * 入账操作：相同幂等键第二次调用返回 applied=false，余额不再变动。
     */
    @Test
    @Transactional
    void duplicateCreditReturnsAppliedFalseWithNoSideEffect() {
        AccountBalance balance = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(() -> new AssertionError("种子余额不存在"));
        BigDecimal initialAvailable = balance.getAvailableAmount();

        AssetOperationRequest req = buildRequest("it-idempotency-credit-1", "5.00000001");

        // 第一次
        AssetOperationResponse first = service.credit(req);
        assertThat(first.isApplied()).isTrue();

        // 第二次
        AssetOperationResponse second = service.credit(req);
        assertThat(second.isApplied()).isFalse();

        // 余额只增加一次
        AccountBalance after = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(AssertionError::new);
        assertThat(after.getAvailableAmount())
                .isEqualByComparingTo(initialAvailable.add(new BigDecimal("5.00000001")));

        // 流水仅一条
        assertThat(ledgerRepository.countByTransferIdAndOperationType("it-idempotency-credit-1", OperationType.CREDIT))
                .isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // 测试 2：数据库唯一索引直接拒绝重复 AssetOperation 插入
    //         这是 AccountRepositoryTest#rejectsDuplicateTransferOperation 的保真版本。
    //         必须在独立事务外执行 flush，否则约束检查延迟到外层事务提交。
    // -------------------------------------------------------------------------

    /**
     * 真实 MySQL 唯一索引拒绝 (transfer_id, operation_type) 重复插入。
     *
     * <p>由于 {@code @DataJpaTest} 默认开启事务回滚，此测试使用 {@code Propagation.NOT_SUPPORTED}
     * 以非事务方式运行（每次 save+flush 立即提交到 MySQL），确保唯一约束在第二次 flush 时触发。
     * 测试结束后手动清理插入的记录。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void realMySqlUniqueIndexRejectsDuplicateAssetOperationInsert() {
        AssetOperation first = AssetOperation.success(
                "it-dup-index-1", OperationType.FREEZE, "user-1", "USDT",
                new BigDecimal("1.00000000"), "success");
        operationRepository.saveAndFlush(first);

        try {
            AssetOperation duplicate = AssetOperation.success(
                    "it-dup-index-1", OperationType.FREEZE, "user-1", "USDT",
                    new BigDecimal("1.00000000"), "success");
            assertThatThrownBy(() -> operationRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            // 清理：删除第一条成功插入的记录，避免污染其他测试
            operationRepository.findByTransferIdAndOperationType("it-dup-index-1", OperationType.FREEZE)
                    .ifPresent(op -> operationRepository.deleteById(op.getId()));
        }
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    private AssetOperationRequest buildRequest(String transferId, String amount) {
        return new AssetOperationRequest(
                transferId, "user-1", "USDT",
                new BigDecimal(amount), TransferDirection.A_TO_B);
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.account.repository")
    @EntityScan(basePackages = "com.demo.transfer.account.domain")
    @Import(AccountAssetService.class)
    static class JpaConfig {
    }
}

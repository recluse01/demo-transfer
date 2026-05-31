package com.demo.transfer.account.support;

import static org.assertj.core.api.Assertions.assertThat;

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
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保真轨高精度金额集成测试（4.2）。
 *
 * <p>H2 对 DECIMAL 精度的处理与 MySQL 8 存在细微差异；本测试在真实 MySQL 容器上验证：
 * DECIMAL(32,8) 列能无损保存和读回 8 位小数金额（如 {@code 0.00000001}），
 * scale 严格等于 8。
 *
 * <p>继承 {@link AbstractMySqlIntegrationTest} 以复用单例 MySQL 容器。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AccountAmountPrecisionIT.JpaConfig.class)
class AccountAmountPrecisionIT extends AbstractMySqlIntegrationTest {

    /** 最小精度测试值：DECIMAL(32,8) 能表示的最小正数。 */
    private static final BigDecimal MIN_PRECISION = new BigDecimal("0.00000001");

    @Autowired
    private AccountBalanceRepository balanceRepository;

    @Autowired
    private AssetOperationRepository operationRepository;

    @Autowired
    private AccountAssetService service;

    @Autowired
    private FinanceLedgerRepository ledgerRepository;

    @Autowired
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // 测试 1：余额表 available_amount 高精度写入读回
    // -------------------------------------------------------------------------

    /**
     * account_balance.available_amount 保存 0.00000001 后读回，值与 scale 均不变。
     *
     * <p>H2 隐式将 scale 裁剪到字面量精度，无法暴露此问题；MySQL 8 DECIMAL(32,8) 严格保持 8 位。
     */
    @Test
    @Transactional
    void balanceAvailableAmountPreservesEightDecimalScale() {
        // 写入最小精度余额
        AccountBalance balance = AccountBalance.create("user-precision-1", "USDT", MIN_PRECISION);
        balanceRepository.saveAndFlush(balance);

        // 清空一级缓存，强制下面的查询从 MySQL 真实回读（否则返回内存中字面量 BigDecimal，断言空洞）
        entityManager.clear();

        // 从数据库读回
        AccountBalance loaded = balanceRepository.findByUserIdAndAssetCode("user-precision-1", "USDT")
                .orElseThrow(() -> new AssertionError("精度测试余额未找到"));

        BigDecimal actual = loaded.getAvailableAmount();

        // 值相等（忽略 scale 差异的比较）
        assertThat(actual).isEqualByComparingTo(MIN_PRECISION);

        // scale 严格等于 8：证明 MySQL DECIMAL(32,8) 对精度的保真
        assertThat(actual.scale())
                .as("MySQL DECIMAL(32,8) 应保留 scale=8，当前 scale=%d", actual.scale())
                .isEqualTo(8);

        // 字符串表示无科学计数法，小数点后恰好 8 位
        assertThat(actual.toPlainString()).isEqualTo("0.00000001");
    }

    /**
     * account_balance.frozen_amount 经冻结操作后，8 位小数 scale 被保留。
     */
    @Test
    @Transactional
    void balanceFrozenAmountPreservesEightDecimalScaleAfterFreeze() {
        // 初始化一个可用余额足够的账户
        AccountBalance balance = AccountBalance.create("user-precision-2", "USDT", new BigDecimal("1.00000000"));
        balanceRepository.saveAndFlush(balance);

        // 冻结最小精度金额
        AssetOperationRequest req = new AssetOperationRequest(
                "it-precision-freeze-1", "user-precision-2", "USDT",
                MIN_PRECISION, TransferDirection.A_TO_B);
        AssetOperationResponse response = service.freeze(req);
        assertThat(response.isApplied()).isTrue();

        // 先 flush 把服务层脏数据写入数据库会话，再 clear 清空一级缓存，
        // 强制下面从 MySQL 真实回读冻结/可用金额（直接 clear 会丢弃未 flush 的脏更新）
        entityManager.flush();
        entityManager.clear();

        // 验证 frozen_amount
        AccountBalance after = balanceRepository.findByUserIdAndAssetCode("user-precision-2", "USDT")
                .orElseThrow(AssertionError::new);

        BigDecimal frozen = after.getFrozenAmount();
        assertThat(frozen).isEqualByComparingTo(MIN_PRECISION);
        assertThat(frozen.scale()).isEqualTo(8);
        assertThat(frozen.toPlainString()).isEqualTo("0.00000001");

        // 可用余额：1.00000000 - 0.00000001 = 0.99999999，scale 同样为 8
        BigDecimal available = after.getAvailableAmount();
        assertThat(available).isEqualByComparingTo(new BigDecimal("0.99999999"));
        assertThat(available.scale()).isEqualTo(8);
    }

    // -------------------------------------------------------------------------
    // 测试 2：asset_operation.amount 高精度写入读回
    // -------------------------------------------------------------------------

    /**
     * asset_operation.amount 保存 0.00000001 后读回，值与 scale 均不变。
     */
    @Test
    @Transactional
    void assetOperationAmountPreservesEightDecimalScale() {
        AssetOperation op = AssetOperation.success(
                "it-precision-op-1", OperationType.CREDIT, "user-1", "USDT",
                MIN_PRECISION, "precision test");
        operationRepository.saveAndFlush(op);

        // 清空一级缓存，强制从 MySQL 真实回读 amount
        entityManager.clear();

        AssetOperation loaded = operationRepository.findByTransferIdAndOperationType(
                "it-precision-op-1", OperationType.CREDIT)
                .orElseThrow(() -> new AssertionError("精度测试操作记录未找到"));

        BigDecimal actual = loaded.getAmount();

        assertThat(actual).isEqualByComparingTo(MIN_PRECISION);
        assertThat(actual.scale())
                .as("MySQL DECIMAL(32,8) 应保留 scale=8，当前 scale=%d", actual.scale())
                .isEqualTo(8);
        assertThat(actual.toPlainString()).isEqualTo("0.00000001");
    }

    // -------------------------------------------------------------------------
    // 测试 3：通过服务层执行入账，高精度金额端到端写入读回
    // -------------------------------------------------------------------------

    /**
     * 通过 {@link AccountAssetService#credit} 端到端入账 0.00000001，
     * 验证余额、流水均以 8 位精度保真存储。
     */
    @Test
    @Transactional
    void creditWithMinPrecisionAmountIsStoredAndReadBackExactly() {
        // 种子数据已有 user-1 / USDT；直接在其上叠加入账
        AccountBalance before = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(() -> new AssertionError("种子余额不存在"));
        BigDecimal initialAvailable = before.getAvailableAmount();

        AssetOperationRequest req = new AssetOperationRequest(
                "it-precision-credit-1", "user-1", "USDT",
                MIN_PRECISION, TransferDirection.A_TO_B);

        AssetOperationResponse response = service.credit(req);
        assertThat(response.isApplied()).isTrue();

        // 先 flush 把服务层脏数据写入数据库会话，再 clear 清空一级缓存，
        // 强制下面从 MySQL 真实回读入账后的可用余额（直接 clear 会丢弃未 flush 的脏更新）
        entityManager.flush();
        entityManager.clear();

        AccountBalance after = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(AssertionError::new);

        BigDecimal expected = initialAvailable.add(MIN_PRECISION);
        BigDecimal actual = after.getAvailableAmount();

        // 值精确相等
        assertThat(actual).isEqualByComparingTo(expected);

        // scale 保持 8 位
        assertThat(actual.scale()).isEqualTo(8);

        // 流水表也已写入（精度由列定义保证，scale 验证在 assetOperation 测试中覆盖）
        assertThat(ledgerRepository.countByTransferIdAndOperationType("it-precision-credit-1", OperationType.CREDIT))
                .isEqualTo(1L);
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.account.repository")
    @EntityScan(basePackages = "com.demo.transfer.account.domain")
    @Import(AccountAssetService.class)
    static class JpaConfig {
    }
}

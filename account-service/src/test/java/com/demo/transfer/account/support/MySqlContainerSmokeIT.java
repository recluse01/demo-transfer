package com.demo.transfer.account.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.transfer.account.domain.AccountBalance;
import com.demo.transfer.account.repository.AccountBalanceRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * 保真轨冒烟测试：验证 Testcontainers MySQL 容器启动、真实 DDL 建表、种子数据可读、JPA 映射与连接均正常。
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} 阻止 Spring 把数据源换回 H2，
 * 改用基类经 {@code @DynamicPropertySource} 注入的真实 MySQL 连接。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MySqlContainerSmokeIT.JpaConfig.class)
class MySqlContainerSmokeIT extends AbstractMySqlIntegrationTest {

    @Autowired
    private AccountBalanceRepository balanceRepository;

    @Test
    void containerStartsAndRealSchemaWithSeedDataIsQueryable() {
        // 容器已由基类静态块启动
        assertThat(MYSQL.isRunning()).isTrue();

        // account_a 初始化脚本播种：user-1 / USDT / 可用 1000.00000000
        AccountBalance seeded = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT")
                .orElseThrow(() -> new AssertionError("应能从真实 account_a 库读到种子余额"));

        // 精确比较验证 DECIMAL(32,8) 列保真
        assertThat(seeded.getAvailableAmount()).isEqualByComparingTo(new BigDecimal("1000.00000000"));
        assertThat(seeded.getFrozenAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.account.repository")
    @EntityScan(basePackages = "com.demo.transfer.account.domain")
    @Configuration
    static class JpaConfig {
    }
}

package com.demo.transfer.transfer.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.transfer.transfer.repository.TransferOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * 保真轨冒烟测试：验证 Testcontainers MySQL 容器启动、真实 DDL 在 transfer 库建表、JPA 映射与连接均正常。
 *
 * <p>transfer 库无种子数据，故以「{@code transfer_order} 表可查询且为空」证明 schema 已建立、连接可用。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MySqlContainerSmokeIT.JpaConfig.class)
class MySqlContainerSmokeIT extends AbstractMySqlIntegrationTest {

    @Autowired
    private TransferOrderRepository orderRepository;

    @Test
    void containerStartsAndRealSchemaIsQueryable() {
        assertThat(MYSQL.isRunning()).isTrue();

        // 表存在则计数成功（空库返回 0）；表不存在会抛异常
        assertThat(orderRepository.count()).isZero();
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Configuration
    static class JpaConfig {
    }
}

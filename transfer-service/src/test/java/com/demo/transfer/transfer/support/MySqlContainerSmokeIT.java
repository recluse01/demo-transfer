package com.demo.transfer.transfer.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
 * <p>transfer 库无种子数据，故以「按不存在的唯一业务键（{@code transferId}）查询返回空 +
 * {@code count()} 不抛异常」证明 {@code transfer_order} 的 schema 已建立、可查询。
 *
 * <p>刻意<strong>不</strong>断言 {@code count()==0}：单例容器跨所有 *IT 复用，
 * 表中可能残留其他写库 IT 的数据（尤其本地开启 {@code withReuse} 后跨 JVM 复用），
 * 「表为空」并非 schema 可查询的必要条件，依赖它会与其他 IT 的清理顺序隐性耦合。
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

        // 按不存在的唯一业务键查询返回空：证明表与唯一键列存在、可被查询，且不依赖表为空
        assertThat(orderRepository.findByTransferId("smoke-it-nonexistent")).isEmpty();

        // count() 不抛异常即证明 schema 已建立、连接可用（不断言具体行数，避免与其他 IT 残留数据耦合）
        assertThatCode(orderRepository::count).doesNotThrowAnyException();
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Configuration
    static class JpaConfig {
    }
}

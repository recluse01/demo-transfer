package com.demo.transfer.transfer.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 保真轨集成测试基类（*IT 继承）。
 *
 * <p>采用单例容器模式：static {@link MySQLContainer} 在 JVM 内只启动一次、跨所有测试类复用，
 * 由 Testcontainers 的 Ryuk 在 JVM 退出时清理，刻意不在 {@code @AfterAll} 停止。
 *
 * <p>容器用<strong>真实 DDL 脚本</strong>（{@code docker/mysql/init/01-demo-transfer.sql}，单一事实来源，
 * 不复制进测试资源以避免漂移）在 {@code /docker-entrypoint-initdb.d} 以 root 初始化，与 docker-compose 完全一致。
 *
 * <p>transfer-service 的实体映射 {@code transfer} 库结构，故连接 {@code transfer}；
 * 因初始化脚本以 root 创建多库，测试亦以 root 连接。
 */
@ActiveProfiles("test")
public abstract class AbstractMySqlIntegrationTest {

    private static final String IMAGE = "mysql:8.0.36";
    /** 实际 DDL 在仓库根 docker/mysql/init 下；测试工作目录为模块目录，故用相对路径上溯一级。 */
    private static final String INIT_SCRIPT_HOST_PATH = "../docker/mysql/init/01-demo-transfer.sql";
    private static final String TARGET_DATABASE = "transfer";

    protected static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>(IMAGE)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(INIT_SCRIPT_HOST_PATH),
                        "/docker-entrypoint-initdb.d/01-demo-transfer.sql");
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getFirstMappedPort()
                + "/" + TARGET_DATABASE
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        registry.add("spring.datasource.url", () -> url);
        // 初始化脚本以 root 建多库，故以 root 访问 transfer 的表
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}

package com.demo.transfer.account;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * account-service 测试专用启动类。
 *
 * <p>account-service 作为被 account-a/b-service 复用的库模块，自身不含 {@code @SpringBootApplication}。
 * 切片测试（如 {@code @WebMvcTest}）需要一个 {@code @SpringBootConfiguration} 锚点，故在测试源码提供本类。
 * 现有显式 {@code @ContextConfiguration} 的 {@code @DataJpaTest} 不依赖它，互不影响。
 */
@SpringBootApplication
public class AccountServiceTestApplication {
}

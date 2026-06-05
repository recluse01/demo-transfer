package com.demo.transfer.accounta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@EntityScan(basePackages = "com.demo.transfer.account.domain")
@EnableJpaRepositories(basePackages = "com.demo.transfer.account.repository")
@SpringBootApplication(scanBasePackages = "com.demo.transfer")
public class AccountAApplication {
    public static void main(String[] args) {
        log.info("正在启动 account-a-service");
        SpringApplication.run(AccountAApplication.class, args);
    }
}

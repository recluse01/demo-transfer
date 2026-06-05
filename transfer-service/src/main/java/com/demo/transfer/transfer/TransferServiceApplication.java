package com.demo.transfer.transfer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableScheduling
@EnableFeignClients(basePackages = "com.demo.transfer.transfer.client")
@SpringBootApplication(scanBasePackages = "com.demo.transfer")
public class TransferServiceApplication {
    public static void main(String[] args) {
        log.info("正在启动 transfer-service");
        SpringApplication.run(TransferServiceApplication.class, args);
    }
}

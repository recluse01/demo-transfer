package com.demo.transfer.transfer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI transferOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI().info(new Info()
                .title("跨账户转账服务 API - " + applicationName)
                .description("用于创建转账、处理审核、接收提现结果和触发失败重试。")
                .version("v1")
                .contact(new Contact().name("demo-transfer"))
                .license(new License().name("Internal Demo Use")));
    }
}

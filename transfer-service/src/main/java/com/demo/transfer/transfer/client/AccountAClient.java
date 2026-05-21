package com.demo.transfer.transfer.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "account-a-service", url = "${account.a.url:http://localhost:8081}")
public interface AccountAClient extends AccountOperationsClient {
}

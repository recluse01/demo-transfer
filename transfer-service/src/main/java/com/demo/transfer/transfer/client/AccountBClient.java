package com.demo.transfer.transfer.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "account-b-service", url = "${account.b.url:http://localhost:8082}")
public interface AccountBClient extends AccountOperationsClient {
}

package com.demo.transfer.transfer.repository;

import com.demo.transfer.transfer.domain.TransferStepLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferStepLogRepository extends JpaRepository<TransferStepLog, Long> {
    List<TransferStepLog> findByTransferIdOrderByCreatedAtAsc(String transferId);
}

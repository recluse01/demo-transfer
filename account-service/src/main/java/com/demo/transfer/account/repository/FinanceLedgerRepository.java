package com.demo.transfer.account.repository;

import com.demo.transfer.account.domain.FinanceLedger;
import com.demo.transfer.common.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceLedgerRepository extends JpaRepository<FinanceLedger, Long> {
    long countByTransferIdAndOperationType(String transferId, OperationType operationType);
}

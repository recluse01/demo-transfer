package com.demo.transfer.account.repository;

import com.demo.transfer.account.domain.AssetOperation;
import com.demo.transfer.common.OperationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetOperationRepository extends JpaRepository<AssetOperation, Long> {
    Optional<AssetOperation> findByTransferIdAndOperationType(String transferId, OperationType operationType);
}

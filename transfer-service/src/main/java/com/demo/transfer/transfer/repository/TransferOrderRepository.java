package com.demo.transfer.transfer.repository;

import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {
    Optional<TransferOrder> findByTransferId(String transferId);

    List<TransferOrder> findTop100ByStatusInOrderByUpdatedAtAsc(Collection<TransferStatus> statuses);
}

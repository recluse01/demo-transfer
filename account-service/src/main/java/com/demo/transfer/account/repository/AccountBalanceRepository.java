package com.demo.transfer.account.repository;

import com.demo.transfer.account.domain.AccountBalance;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Long> {
    Optional<AccountBalance> findByUserIdAndAssetCode(String userId, String assetCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalance b where b.userId = :userId and b.assetCode = :assetCode")
    Optional<AccountBalance> findByUserIdAndAssetCodeForUpdate(@Param("userId") String userId,
            @Param("assetCode") String assetCode);
}

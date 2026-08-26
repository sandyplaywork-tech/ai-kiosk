package com.aikiosk.backend.voucher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    Optional<Voucher> findByUsername(String username);

    Optional<Voucher> findByVoucherCode(String voucherCode);

    /**
     * Atomic compare-and-set so concurrent logins on the same single-use
     * voucher can't both succeed - only the request that observes the
     * expected status wins the row update.
     */
    @Modifying
    @Query("UPDATE Voucher v SET v.status = :newStatus, v.activatedAt = CURRENT_TIMESTAMP "
            + "WHERE v.id = :id AND v.status = :expectedStatus")
    int updateStatusIfCurrentStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") VoucherStatus expectedStatus,
            @Param("newStatus") VoucherStatus newStatus);
}

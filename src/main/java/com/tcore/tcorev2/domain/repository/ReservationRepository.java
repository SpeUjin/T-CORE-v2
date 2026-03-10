package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Reservation;
import com.tcore.tcorev2.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    // CAS 패턴: id가 일치하면서, 현재 상태가 PENDING일 때만 CONFIRMED로 바꾼다!
    // 반환값(int)은 "성공적으로 업데이트 된 데이터의 개수" 입니다.
    @Modifying(clearAutomatically = true) // 영속성 컨텍스트 자동 클리어 (선택 사항)
    @Query("UPDATE Reservation r SET r.status = :newStatus WHERE r.id = :id AND r.status = :expectedStatus")
    int updateStatusWithCAS(@Param("id") Long id,
                            @Param("newStatus") ReservationStatus newStatus,
                            @Param("expectedStatus") ReservationStatus expectedStatus);

    /**
     * [Safety Net] 만료된 PENDING 상태의 예약을 조회합니다.
     * N+1 문제를 방지하기 위해 JOIN FETCH를 사용하여 연관된 Seat 데이터를 한 번의 쿼리로 함께 가져옵니다.
     * 앞서 설정한 복합 인덱스(idx_status_reserved_at)를 타기 때문에 검색 속도가 매우 빠릅니다.
     */
    @Query("SELECT r FROM Reservation r JOIN FETCH r.seat " +
            "WHERE r.status = :status AND r.reservedAt <= :cutoffTime")
    List<Reservation> findExpiredReservations(@Param("status") ReservationStatus status,
                                              @Param("cutoffTime") LocalDateTime cutoffTime);
}

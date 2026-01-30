package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    // 특정 일정의 모든 좌석을 조회 (예매 화면용)
    List<Seat> findByScheduleId(Long scheduleId);
    long countByScheduleIdAndStatus(Long scheduleId, SeatStatus status);
}

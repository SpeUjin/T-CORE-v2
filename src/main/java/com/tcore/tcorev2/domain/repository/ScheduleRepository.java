package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    // 특정 공연의 모든 일정을 찾는 메서드 (나중에 필요함)
    List<Schedule> findByConcertId(Long concertId);
}

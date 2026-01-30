package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.api.dto.ConcertResponse;
import com.tcore.tcorev2.api.dto.ScheduleResponse;
import com.tcore.tcorev2.domain.model.SeatStatus;
import com.tcore.tcorev2.domain.repository.ConcertRepository;
import com.tcore.tcorev2.domain.repository.ScheduleRepository;
import com.tcore.tcorev2.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공연 정보 및 일정 관리를 담당하는 애플리케이션 서비스
 * 도메인 모델과 리포지토리를 조합하여 공연 관련 비즈니스 로직을 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 성능 최적화: 스냅샷 생성 및 변경 감지 오버헤드 방지
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;

    /**
     * 시스템에 등록된 모든 공연 목록을 조회합니다.
     * * @return {@link ConcertResponse} 리스트
     */
    public List<ConcertResponse> getAllConcerts() {
        return concertRepository.findAll().stream()
                .map(concert -> ConcertResponse.builder()
                        .id(concert.getId())
                        .title(concert.getTitle())
                        .artist(concert.getArtist())
                        .build())
                .toList();
    }

    /**
     * 특정 공연에 해당하는 상세 일정과 각 일정별 잔여 좌석 수를 조회합니다.
     * * @param concertId 공연 식별자
     * @return {@link ScheduleResponse} 리스트 (공연 시간 및 잔여 좌석 포함)
     */
    public List<ScheduleResponse> getSchedulesByConcert(Long concertId) {
        return scheduleRepository.findByConcertId(concertId).stream()
                .map(schedule -> ScheduleResponse.builder()
                        .id(schedule.getId())
                        .startTime(schedule.getStartTime())
                        .availableSeatCount(seatRepository.countByScheduleIdAndStatus(schedule.getId(), SeatStatus.AVAILABLE))
                        .build())
                .toList();
    }
}
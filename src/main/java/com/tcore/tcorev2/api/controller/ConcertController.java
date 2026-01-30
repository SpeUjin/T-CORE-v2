package com.tcore.tcorev2.api.controller;

import com.tcore.tcorev2.api.dto.ConcertResponse;
import com.tcore.tcorev2.api.dto.ScheduleResponse;
import com.tcore.tcorev2.application.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 공연 및 일정 조회 관련 API를 제공하는 REST 컨트롤러
 * 진입점: /api/v1/concerts
 */
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    /**
     * [GET] 전체 공연 목록 조회
     * 시스템에서 예매 가능한 모든 공연 리스트를 반환합니다.
     * * @return 200 OK, 공연 목록
     */
    @GetMapping
    public ResponseEntity<List<ConcertResponse>> getConcerts() {
        return ResponseEntity.ok(concertService.getAllConcerts());
    }

    /**
     * [GET] 특정 공연의 상세 일정 및 잔여 좌석 조회
     * 공연의 ID를 기반으로 해당 공연의 날짜별 시간대와 예약 가능한 좌석 수를 반환합니다.
     * * @param concertId 공연 ID (Path Variable)
     * @return 200 OK, 일정 및 좌석 정보 리스트
     */
    @GetMapping("/{concertId}/schedules")
    public ResponseEntity<List<ScheduleResponse>> getSchedules(@PathVariable Long concertId) {
        return ResponseEntity.ok(concertService.getSchedulesByConcert(concertId));
    }
}
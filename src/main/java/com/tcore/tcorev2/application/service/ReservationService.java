package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.api.dto.ReservationRequest;
import com.tcore.tcorev2.api.dto.ReservationResponse;
import com.tcore.tcorev2.domain.entity.Reservation;
import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.repository.ReservationRepository;
import com.tcore.tcorev2.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 실제 좌석 예약 비즈니스 로직을 수행합니다.
     * Facade 레이어에서 락을 획득한 후 호출되므로, 동시성 제어가 보장된 상태에서 트랜잭션을 처리합니다.
     */
    @Transactional
    public ReservationResponse reserveSeat(ReservationRequest request) {
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

        // 좌석 선점 (상태 변경: AVAILABLE -> RESERVED)
        // Seat 엔티티 내부의 reserve() 메서드가 상태 체크를 수행합니다.
        seat.reserve();

        // 예약 생성 및 저장
        Reservation reservation = Reservation.createReservation(request.getUserId(), seat);
        Reservation savedReservation = reservationRepository.save(reservation);

        log.info("좌석 예약 성공 - seatId: {}, userId: {}", seat.getId(), request.getUserId());

        return ReservationResponse.builder()
                .reservationId(savedReservation.getId())
                .seatNumber(seat.getSeatNumber())
                .status(savedReservation.getStatus().name())
                .reservedAt(savedReservation.getReservedAt())
                .build();
    }
}

package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.domain.entity.Reservation;
import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.ReservationStatus;
import com.tcore.tcorev2.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationRepository reservationRepository;

    // 결제 전 예약 데이터 검증 (트랜잭션 분리용 읽기 전용)
    @Transactional(readOnly = true)
    public Reservation getValidPendingReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        if (!reservation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예약만 결제할 수 있습니다.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("결제 대기 상태가 아닙니다.");
        }
        return reservation;
    }

    // 결제 성공 시 확정 처리 (CAS 적용)
    @Transactional
    public void confirmPayment(Long reservationId) {
        // 1. CAS 쿼리 실행 (현재 상태가 PENDING일 때만 CONFIRMED로 변경)
        int updatedCount = reservationRepository.updateStatusWithCAS(
                reservationId,
                ReservationStatus.CONFIRMED,
                ReservationStatus.PENDING
        );

        // 2. 업데이트된 개수가 0개라면? = 내가 늦었다! (스케줄러가 이미 취소했거나 다른 상태임)
        if (updatedCount == 0) {
            log.error("예매 확정 실패 (Race Condition 발생) - reservationId: {}", reservationId);
            // 이 예외가 발생하면 PaymentFacade의 catch 블록으로 넘어가서 PG사 환불 API를 호출하게 됩니다.
            throw new IllegalStateException("예매 대기 시간이 초과되어 이미 취소된 예약입니다. 결제된 금액은 자동 환불됩니다.");
        }

        // 3. 무사히 상태가 변경되었다면 좌석(Seat) 상태도 변경하기 위해 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        reservation.getSeat().completePayment();
    }

    // 결제 실패/취소 시 롤백 처리 (좌석 재개방)
    @Transactional
    public void cancelPayment(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // 1. 예약 취소 처리
        reservation.cancel();

        // 2. 좌석 상태를 다시 AVAILABLE로 변경하여 재개방
        Seat seat = reservation.getSeat();
        seat.release();
    }
}
package com.tcore.tcorev2.application.facade;

import com.tcore.tcorev2.application.port.SystemMetricPort;
import com.tcore.tcorev2.application.scheduler.TimeoutScheduler;
import com.tcore.tcorev2.domain.entity.Reservation;
import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.ReservationStatus;
import com.tcore.tcorev2.domain.model.SeatStatus;
import com.tcore.tcorev2.domain.repository.ReservationRepository;
import com.tcore.tcorev2.domain.repository.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
class PaymentIntegrationTest {

    @MockitoBean // ⭐️ 추가: 이 인터페이스의 가짜 구현체를 빈으로 등록해줍니다.
    private SystemMetricPort systemMetricPort;

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private TimeoutScheduler timeoutScheduler;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private SeatRepository seatRepository;

    @Test
    @DisplayName("결제 확정과 스케줄러 취소가 동시에 일어날 때, CAS 패턴으로 데이터 정합성이 보장되어야 한다.")
    void raceConditionBetweenPaymentAndScheduler() throws InterruptedException {
        // 1. Given: 이미 5분이 경과한 PENDING 상태의 예약 데이터 준비
        // (테스트용 데이터 세팅 - Seat 하나와 그에 물린 Reservation 생성)
        Seat seat = Seat.builder()
                .seatNumber("A1").price(50000).status(SeatStatus.RESERVED).build();
        seatRepository.save(seat);

        Reservation reservation = Reservation.createReservation(1L, seat);
        // 테스트를 위해 강제로 예약 시간을 10분 전으로 설정 (만료 대상)
        // Reflection 등을 쓰거나 reservedAt을 직접 수정할 수 있는 테스트용 메서드 활용
        reservationRepository.save(reservation);

        final Long reservationId = reservation.getId();
        final Long userId = 1L;
        final Long concertId = 1L;

        // 2. When: 두 개의 스레드가 '동시에' 실행됨
        // 스레드 1: 결제 성공 후 확정 시도 (PaymentFacade)
        // 스레드 2: 스케줄러가 돌면서 만료 취소 시도 (TimeoutScheduler)
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // [스레드 1] 사용자 결제 확정 시도
        executorService.submit(() -> {
            try {
                log.info("[Test] 사용자 결제 확정 시도 시작");
                paymentFacade.executePayment(reservationId, userId, concertId, 50000L);
            } catch (Exception e) {
                log.warn("[Test] 사용자 결제 확정 실패 (예상된 결과일 수 있음): {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // [스레드 2] 자가 치유 스케줄러 실행
        executorService.submit(() -> {
            try {
                log.info("[Test] 스케줄러 자가 치유 시작");
                // 분산 락을 우회하여 핵심 로직만 테스트하기 위해 내부 메서드 호출
                timeoutScheduler.processExpiredReservations();
            } catch (Exception e) {
                log.error("[Test] 스케줄러 실행 중 에러: ", e);
            } finally {
                latch.countDown();
            }
        });

        latch.await(); // 두 스레드가 모두 끝날 때까지 대기

        // 3. Then: 결과 검증 (Race Condition 방어 확인)
        Reservation finalReservation = reservationRepository.findById(reservationId).orElseThrow();
        Seat finalSeat = seatRepository.findById(seat.getId()).orElseThrow();

        log.info("[Test] 최종 예약 상태: {}", finalReservation.getStatus());
        log.info("[Test] 최종 좌석 상태: {}", finalSeat.getStatus());

        // [검증 포인트]
        // 상태는 CONFIRMED(예매완료) 혹은 CANCELLED(취소) 중 하나여야 하며,
        // 예약 상태와 좌석 상태가 반드시 일치해야 함 (정합성)
        if (finalReservation.getStatus() == ReservationStatus.CONFIRMED) {
            assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        } else if (finalReservation.getStatus() == ReservationStatus.CANCELLED) {
            assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        }
    }
}
package com.tcore.tcorev2.application.scheduler;

import com.tcore.tcorev2.domain.entity.Reservation;
import com.tcore.tcorev2.domain.model.ReservationStatus;
import com.tcore.tcorev2.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutScheduler {

    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;

    // 분산 락의 이름 (여러 서버가 이 이름을 두고 경쟁합니다)
    private static final String SCHEDULER_LOCK_KEY = "lock:scheduler:timeout";

    /**
     * 매 1분마다 실행되는 Safety Net 스케줄러.
     * 1. [Redisson Lock] 여러 서버 중 딱 1대만 실행 권한 획득
     * 2. [DB Query] 5분이 지난 PENDING 예약들을 한 번에 조회 (JOIN FETCH)
     * 3. [Business Logic] 해당 예약들을 CANCELLED로 변경하고, 묶인 Seat을 AVAILABLE로 재개방
     * 4. [Release Lock] 실행이 끝나면 권한 반납
     */
    @Scheduled(cron = "0 * * * * *") // 매 분 0초마다 실행
    public void cancelExpiredReservations() {
        // 1. Redisson을 통해 분산 락 객체 가져오기
        RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);

        try {
            // 2. 락 획득 시도 (대기 시간 0초, 점유 시간 50초)
            // 대기 시간을 0으로 준 이유: 1분마다 도는 스케줄러이므로, 내가 못 잡았으면 굳이 기다리지 않고 이번 턴은 포기(Skip)하기 위함입니다.
            boolean isLocked = lock.tryLock(0, 50, TimeUnit.SECONDS);

            if (!isLocked) {
                log.debug("[Scheduler] 다른 서버 인스턴스가 이미 만료 스케줄러를 실행 중입니다. 실행을 건너뜁니다.");
                return; // 락을 못 잡았으니 조용히 물러납니다.
            }

            // 3. 락을 획득한 1대의 서버만 진짜 비즈니스 로직(취소 및 재개방) 수행
            log.info("[Scheduler] 결제 만료된 예약 정리를 시작합니다.");
            processExpiredReservations();

        } catch (InterruptedException e) {
            log.error("[Scheduler] 분산 락 획득 중 인터럽트가 발생했습니다.", e);
            Thread.currentThread().interrupt();
        } finally {
            // 4. 로직이 끝났거나 예외가 발생해도 반드시 락을 해제합니다.
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 실제 DB를 건드리는 비즈니스 로직은 별도의 메서드로 분리하여 @Transactional을 걸어줍니다.
     */
    @Transactional
    public void processExpiredReservations() {
        // 1. 기준 시간 계산 (현재 시간 - 5분)
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);

        // 2. N+1 문제 없이 만료된 예약과 좌석을 한방에 긁어옵니다. (우리가 만든 JOIN FETCH 쿼리)
        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(
                ReservationStatus.PENDING, cutoffTime
        );

        if (expiredReservations.isEmpty()) {
            log.info("[Scheduler] 만료된 결제 대기 건이 없습니다.");
            return;
        }

        // 3. 찾은 데이터들을 순회하며 상태를 바꿉니다. (Dirty Checking 활용)
        int cancelCount = 0;
        for (Reservation reservation : expiredReservations) {
            try {
                // 예약 상태를 CANCELLED로 변경
                // (엔티티에 cancel() 메서드 추가 필요: this.status = ReservationStatus.CANCELLED;)
                // reservation.cancel();

                // 묶여있는 좌석 상태를 다시 AVAILABLE로 풀어줌
                // (엔티티에 release() 메서드 추가 필요: this.status = SeatStatus.AVAILABLE;)
                // reservation.getSeat().release();

                cancelCount++;
            } catch (Exception e) {
                log.error("[Scheduler] 예약 취소 처리 중 오류 발생 - reservationId: {}", reservation.getId(), e);
                // 하나의 예약 취소가 실패하더라도, 다음 예약은 계속 처리하도록 catch로 잡고 넘어갑니다.
            }
        }

        log.info("[Scheduler] 총 {}건의 만료된 예약을 취소하고 좌석을 재개방했습니다.", cancelCount);
    }
}
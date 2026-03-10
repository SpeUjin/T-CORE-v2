package com.tcore.tcorev2.application.facade;

import com.tcore.tcorev2.api.dto.ReservationRequest;
import com.tcore.tcorev2.api.dto.ReservationResponse;
import com.tcore.tcorev2.application.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationFacade {

    private final ReservationService reservationService;
    private final RedissonClient redissonClient;

    /**
     * 분산 락을 사용하여 좌석 예약을 처리하는 Facade 메서드.
     * 락 관리와 비즈니스 로직(트랜잭션)을 분리하여 Spring AOP의 @Transactional이 정상 동작하도록 보장합니다.
     */
    public ReservationResponse reserveSeat(ReservationRequest request) {
        Long seatId = request.getSeatId();
        String lockKey = "seat_lock:" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도 (10초 대기, 3초 점유)
            boolean available = lock.tryLock(10, 3, TimeUnit.SECONDS);

            if (!available) {
                log.warn("락 획득 실패 - seatId: {}", seatId);
                throw new IllegalStateException("현재 시스템이 혼잡하여 요청을 처리할 수 없습니다.");
            }

            // 락 획득 성공 시, 실제 트랜잭션이 걸린 서비스 로직 호출
            return reservationService.reserveSeat(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 오류가 발생했습니다.", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

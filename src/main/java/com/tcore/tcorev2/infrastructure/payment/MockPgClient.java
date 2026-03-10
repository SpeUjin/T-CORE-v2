package com.tcore.tcorev2.infrastructure.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockPgClient {

    /**
     * 외부 PG사 결제 요청을 시뮬레이션 합니다.
     * 이 메서드는 DB 트랜잭션 밖에서 실행되어야 합니다.
     */
    public boolean processPayment(Long userId, Long amount) {
        log.info("[Mock PG] 결제 요청 시작 - userId: {}, amount: {}", userId, amount);
        try {
            // 외부 API 네트워크 통신 지연 가설 (0.5초 ~ 1.5초)
            long delay = (long) (Math.random() * 1000) + 500;
            Thread.sleep(delay);

            // 80% 확률로 결제 성공, 20% 확률로 잔액 부족 등 실패 시뮬레이션
            boolean isSuccess = Math.random() < 0.8;
            log.info("[Mock PG] 결제 응답 완료 - success: {} (delay: {}ms)", isSuccess, delay);
            return isSuccess;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Mock PG] 결제 처리 중 인터럽트 발생", e);
            return false;
        }
    }
}
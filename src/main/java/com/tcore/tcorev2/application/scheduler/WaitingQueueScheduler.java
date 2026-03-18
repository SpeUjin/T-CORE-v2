package com.tcore.tcorev2.application.scheduler;

import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingQueueScheduler {

    private final RedisWaitingRoomService waitingRoomService;

    /**
     * [Phase 4 & 6 통합 스케줄러]
     * 1초마다 실행되며, 대기열(ZSet)에 있는 유저를 활성 상태(Active)로 전환합니다.
     * AI 관제사가 조절한 밸브(Rate Limit) 값을 동적으로 읽어와서 적용합니다.
     */
    @Scheduled(fixedRate = 1000) // 1초(1000ms)마다 무한 반복
    public void activateUsersPeriodically() {
        // 테스트를 위해 1번 콘서트를 타겟으로 잡습니다.
        Long concertId = 1L;

        try {
            // 1. AI가 설정한 (또는 기본값인) 현재 초당 허용량(Rate Limit)을 읽어옵니다.
            int currentRateLimit = waitingRoomService.getRateLimit(concertId);

            // 2. 읽어온 허용량(10명 or 100명)만큼만 대기열에서 꺼내서 입장(Active)시킵니다.
            waitingRoomService.activateUsers(concertId, currentRateLimit);

            // 너무 많이 찍히면 시끄러우니 debug 레벨로 남깁니다. (application.yml에서 debug 켜면 보임)
            log.debug("[문지기] 콘서트 {} - 이번 초에 {}명의 유저를 통과시켰습니다.", concertId, currentRateLimit);

        } catch (Exception e) {
            log.error("[문지기] 대기열 활성화 처리 중 에러 발생", e);
        }
    }
}
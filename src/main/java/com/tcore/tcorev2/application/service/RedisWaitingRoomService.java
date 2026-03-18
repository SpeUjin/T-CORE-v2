package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.api.dto.request.EnterWaitingRoomRequest;
import com.tcore.tcorev2.api.dto.response.WaitingRoomStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisWaitingRoomService {

    private final RedissonClient redissonClient;

    private static final String WAITING_KEY_PREFIX = "waiting:concert:";
    private static final String ACTIVE_KEY_PREFIX = "active:concert:";
    
    // 활성 상태 유지 시간 (예: 5분 내에 결제 진입 안하면 만료)
    private static final long ACTIVE_USER_TTL = 5 * 60; 

    /**
     * 대기열 진입
     * Redis ZSet을 사용하여 대기열에 유저를 추가합니다.
     * Score: 현재 시간 (먼저 온 사람이 우선)
     */
    public void enterQueue(EnterWaitingRoomRequest request) {
        String key = WAITING_KEY_PREFIX + request.getConcertId();
        String value = request.getUserId().toString();

        RScoredSortedSet<String> waitingQueue = redissonClient.getScoredSortedSet(key);
        
        // 이미 대기열에 있는지 확인 (Double check 방지)
        Double score = waitingQueue.getScore(value);
        if (score != null) {
            log.info("User {} is already in the queue.", request.getUserId());
            return;
        }

        // 대기열 추가
        waitingQueue.add(System.currentTimeMillis(), value);
        log.info("User {} entered the queue for concert {}", request.getUserId(), request.getConcertId());
    }

    /**
     * 나의 대기 순번 및 상태 조회
     */
    public WaitingRoomStatusResponse getWaitingStatus(Long userId, Long concertId) {
        String waitingKey = WAITING_KEY_PREFIX + concertId;
        String activeKey = ACTIVE_KEY_PREFIX + concertId;
        String userValue = userId.toString();

        // 1. 활성 리스트(입장 가능 상태)에 있는지 확인
        RSet<String> activeQueue = redissonClient.getSet(activeKey);
        if (activeQueue.contains(userValue)) {
            return WaitingRoomStatusResponse.builder()
                    .userId(userId)
                    .rank(0L)
                    .status("ACTIVE")
                    .estimatedWaitSeconds(0L)
                    .build();
        }

        // 2. 대기열(Waiting Queue)에서 순번 확인
        RScoredSortedSet<String> waitingQueue = redissonClient.getScoredSortedSet(waitingKey);
        Integer rank = waitingQueue.rank(userValue);

        if (rank == null) {
            // 대기열에도 없고 활성 상태도 아님 -> 대기열 진입 필요 or 만료됨
            return WaitingRoomStatusResponse.builder()
                    .userId(userId)
                    .rank(-1L)
                    .status("EXPIRED") // 혹은 NOT_FOUND
                    .estimatedWaitSeconds(0L)
                    .build();
        }

        // 3. 대기 상태 반환
        // 순번은 0부터 시작하므로 +1 처리
        long finalRank = rank + 1;
        
        // 예상 대기 시간 계산 (단순 로직: 10초당 100명 빠진다고 가정)
        // 실제로는 AI 에이전트가 처리 속도를 동적으로 분석하여 값을 조정할 예정
        long estimatedSeconds = (finalRank / 100) * 10; 

        return WaitingRoomStatusResponse.builder()
                .userId(userId)
                .rank(finalRank)
                .status("WAITING")
                .estimatedWaitSeconds(estimatedSeconds)
                .build();
    }

    /**
     * [관리자/시스템/AI] 대기열 유저를 활성 상태로 전환 (입장 허용)
     * 주기적으로 스케줄러가 호출하거나 AI가 트래픽 상황에 맞춰 호출합니다.
     * @param count 입장 시킬 인원 수
     */
    public void activateUsers(Long concertId, long count) {
        String waitingKey = WAITING_KEY_PREFIX + concertId;
        String activeKey = ACTIVE_KEY_PREFIX + concertId;

        RScoredSortedSet<String> waitingQueue = redissonClient.getScoredSortedSet(waitingKey);
        RSet<String> activeQueue = redissonClient.getSet(activeKey);

        // 1. 대기열에서 상위 count명 추출
        // Redisson의 valueRange는 index 기반 조회를 지원합니다.
        Collection<String> targetUsers = waitingQueue.valueRange(0, (int) count - 1);

        if (targetUsers == null || targetUsers.isEmpty()) {
            return;
        }

        // 2. 활성 상태로 이동
        for (String userId : targetUsers) {
            // Active Set에 추가
            activeQueue.add(userId);
            // 대기열에서 제거
            waitingQueue.remove(userId);
        }
        
        // Active Key 만료 시간 설정
        activeQueue.expire(ACTIVE_USER_TTL, TimeUnit.SECONDS);

        log.info("Activated {} users for concert {}", targetUsers.size(), concertId);
    }
    
    /**
     * 유저가 유효한 토큰(활성 상태)을 가지고 있는지 검증
     * Interceptor나 Filter에서 사용
     */
    public boolean isUserActive(Long userId, Long concertId) {
        String activeKey = ACTIVE_KEY_PREFIX + concertId;
        RSet<String> activeQueue = redissonClient.getSet(activeKey);
        return activeQueue.contains(userId.toString());
    }

    /**
     * 유저를 활성 상태(Active Queue)에서 제거합니다.
     * 결제 성공/실패/취소 등 예매 플로우가 끝났을 때 즉시 호출하여 대기열 회전율을 높입니다.
     */
    public void removeActiveUser(Long userId, Long concertId) {
        String activeKey = ACTIVE_KEY_PREFIX + concertId;
        RSet<String> activeQueue = redissonClient.getSet(activeKey);

        // Java의 Set.remove()와 완전히 동일하게 동작합니다.
        // 내부적으로 Redis의 SREM 명령어를 실행하여 O(1)의 시간 복잡도로 빠르게 삭제합니다.
        boolean isRemoved = activeQueue.remove(userId.toString());

        if (isRemoved) {
            log.info("유저 {} 님이 예매 플로우를 종료하여 Active Queue에서 제거되었습니다. (Concert: {})", userId, concertId);
        } else {
            // 이미 만료되었거나 지워진 경우 false를 반환합니다.
            log.debug("유저 {} 님은 이미 Active Queue에 존재하지 않습니다. (Concert: {})", userId, concertId);
        }
    }

    /**
     * 특정 콘서트의 현재 대기열(ZSet)에 남아있는 총 인원수를 반환합니다.
     * 시간 복잡도: O(1) - Redis의 ZCARD 명령어를 사용하여 매우 빠르게 동작합니다.
     */
    public long getWaitingQueueSize(Long concertId) {
        // 유진님이 기존에 ZSet을 만들 때 사용하셨던 Key 이름 포맷으로 맞춰주세요!
        // 예: "queue:waiting:" + concertId
        String queueKey = "queue:waiting:" + concertId;

        // Redisson을 통해 ZSet 객체를 가져와서 사이즈를 리턴합니다.
        return redissonClient.getScoredSortedSet(queueKey).size();
    }
    private static final int DEFAULT_RATE_LIMIT = 50; // 기본 초당 입장 인원

    /**
     * [Phase 6: AI Ops] AI가 결정한 새로운 입장 허용량을 Redis에 저장합니다.
     */
    public void setRateLimit(Long concertId, int allowedUsersPerSecond) {
        String rateLimitKey = "queue:rate_limit:" + concertId;
        // Redisson의 Bucket을 이용해 단순 문자열/숫자를 저장합니다.
        redissonClient.getBucket(rateLimitKey).set(allowedUsersPerSecond);
        log.info("[Valve] 콘서트 {}의 입장 허용량이 {}명으로 변경되었습니다.", concertId, allowedUsersPerSecond);
    }

    /**
     * [Phase 6: AI Ops] 현재 설정된 입장 허용량을 가져옵니다.
     * 스케줄러가 대기열 유저를 활성화(Active) 시킬 때 이 값을 읽어서 사용해야 합니다!
     */
    public int getRateLimit(Long concertId) {
        String rateLimitKey = "queue:rate_limit:" + concertId;
        Object limit = redissonClient.getBucket(rateLimitKey).get();

        // AI가 아직 값을 설정하지 않았다면 기본값을 반환합니다.
        return limit != null ? (Integer) limit : DEFAULT_RATE_LIMIT;
    }
}

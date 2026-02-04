package com.tcore.tcorev2.application.service;

import com.tcore.tcorev2.api.dto.request.EnterWaitingRoomRequest;
import com.tcore.tcorev2.api.dto.response.WaitingRoomStatusResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisWaitingRoomServiceTest {

    @Autowired
    private RedisWaitingRoomService waitingRoomService;

    @Autowired
    private RedissonClient redissonClient; // StringRedisTemplate 대신 RedissonClient 사용

    private final Long concertId = 100L;
    private final String waitingKey = "waiting:concert:" + concertId;
    private final String activeKey = "active:concert:" + concertId;

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리 (RedissonClient 사용)
        redissonClient.getScoredSortedSet(waitingKey).delete();
        redissonClient.getSet(activeKey).delete();
    }

    @Test
    @DisplayName("사용자가 대기열에 진입하면 Redis ZSet에 추가되고 순번이 부여되어야 한다.")
    void enterQueue_and_check_rank() {
        // given
        int numberOfUsers = 10;
        for (long i = 1; i <= numberOfUsers; i++) {
            EnterWaitingRoomRequest request = new EnterWaitingRoomRequest();
            ReflectionTestUtils.setField(request, "userId", i);
            ReflectionTestUtils.setField(request, "concertId", concertId);
            waitingRoomService.enterQueue(request);
        }

        // when & then
        // 1번 유저는 1등이어야 함 (Rank는 0부터 시작하므로 +1)
        WaitingRoomStatusResponse response1 = waitingRoomService.getWaitingStatus(1L, concertId);
        assertThat(response1.getRank()).isEqualTo(1L);
        assertThat(response1.getStatus()).isEqualTo("WAITING");

        // 5번 유저는 5등이어야 함
        WaitingRoomStatusResponse response5 = waitingRoomService.getWaitingStatus(5L, concertId);
        assertThat(response5.getRank()).isEqualTo(5L);

        // RedissonClient ZSet 크기 확인
        RScoredSortedSet<String> waitingQueue = redissonClient.getScoredSortedSet(waitingKey);
        assertThat(waitingQueue.size()).isEqualTo(numberOfUsers);
    }

    @Test
    @DisplayName("대기열에 있는 유저를 활성화(Active)하면 상태가 변경되고 대기열에서 제거되어야 한다.")
    void activateUsers_test() {
        // given
        // 5명 대기열 추가
        for (long i = 1; i <= 5; i++) {
            EnterWaitingRoomRequest request = new EnterWaitingRoomRequest();
            ReflectionTestUtils.setField(request, "userId", i);
            ReflectionTestUtils.setField(request, "concertId", concertId);
            waitingRoomService.enterQueue(request);
        }

        // when
        // 상위 3명 활성화
        waitingRoomService.activateUsers(concertId, 3);

        // then
        // 1. 활성화된 유저 확인 (1~3번)
        for (long i = 1; i <= 3; i++) {
            WaitingRoomStatusResponse status = waitingRoomService.getWaitingStatus(i, concertId);
            assertThat(status.getStatus()).isEqualTo("ACTIVE");
            assertThat(status.getRank()).isEqualTo(0L); // Active 상태는 Rank 0
            
            // RedissonClient Set 상태 확인
            RSet<String> activeQueue = redissonClient.getSet(activeKey);
            assertThat(activeQueue.contains(String.valueOf(i))).isTrue();
        }

        // 2. 아직 대기중인 유저 확인 (4~5번)
        WaitingRoomStatusResponse status4 = waitingRoomService.getWaitingStatus(4L, concertId);
        assertThat(status4.getStatus()).isEqualTo("WAITING");
        assertThat(status4.getRank()).isEqualTo(1L); // 1,2,3번이 빠졌으므로 4번이 1등

        // 3. RedissonClient ZSet 크기 확인 (2명 남음)
        RScoredSortedSet<String> waitingQueue = redissonClient.getScoredSortedSet(waitingKey);
        assertThat(waitingQueue.size()).isEqualTo(2L);
    }
}

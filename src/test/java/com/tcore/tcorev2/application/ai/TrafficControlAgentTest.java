package com.tcore.tcorev2.application.ai;

import com.tcore.tcorev2.api.dto.request.EnterWaitingRoomRequest;
import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import com.tcore.tcorev2.infrastructure.monitoring.MockSystemMetricAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TrafficControlAgentTest {

    @Autowired
    private TrafficControlAgent trafficControlAgent;

    @Autowired
    private MockSystemMetricAdapter mockSystemMetricAdapter;

    @Autowired
    private RedisWaitingRoomService waitingRoomService;
    
    @Autowired
    private RedissonClient redissonClient;

    private static final Long CONCERT_ID = 1L;

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리
        redissonClient.getScoredSortedSet("waiting:concert:" + CONCERT_ID).delete();
        redissonClient.getSet("active:concert:" + CONCERT_ID).delete();
        mockSystemMetricAdapter.reset(); // Mock 데이터 초기화
    }

    @Test
    @DisplayName("CPU 부하가 낮을 때 Agent는 많은 유저를 활성화해야 한다.")
    void controlTraffic_low_cpu() {
        // given
        // 1. CPU 부하 낮음 설정 (20%)
        mockSystemMetricAdapter.setCpuUsage(20.0);
        
        // 2. 대기열에 100명 추가
        addUsersToQueue(100);

        // when
        // Agent 실행 (스케줄링 대신 직접 호출하여 테스트)
        trafficControlAgent.controlTraffic();

        // then
        // 기본 활성화 수(10)의 2배인 20명이 활성화되어야 함
        RSet<String> activeQueue = redissonClient.getSet("active:concert:" + CONCERT_ID);
        assertThat(activeQueue.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("CPU 부하가 높을 때 Agent는 적은 유저만 활성화해야 한다.")
    void controlTraffic_high_cpu() {
        // given
        // 1. CPU 부하 높음 설정 (80%)
        mockSystemMetricAdapter.setCpuUsage(80.0);
        
        // 2. 대기열에 100명 추가
        addUsersToQueue(100);

        // when
        trafficControlAgent.controlTraffic();

        // then
        // 기본 활성화 수(10)의 절반인 5명이 활성화되어야 함
        RSet<String> activeQueue = redissonClient.getSet("active:concert:" + CONCERT_ID);
        assertThat(activeQueue.size()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("CPU 부하가 정상일 때 Agent는 기본 수의 유저를 활성화해야 한다.")
    void controlTraffic_normal_cpu() {
        // given
        // 1. CPU 부하 정상 설정 (50%)
        mockSystemMetricAdapter.setCpuUsage(50.0);
        
        // 2. 대기열에 100명 추가
        addUsersToQueue(100);

        // when
        trafficControlAgent.controlTraffic();

        // then
        // 기본 활성화 수(10)명이 활성화되어야 함
        RSet<String> activeQueue = redissonClient.getSet("active:concert:" + CONCERT_ID);
        assertThat(activeQueue.size()).isEqualTo(10);
    }

    private void addUsersToQueue(int count) {
        for (long i = 1; i <= count; i++) {
            EnterWaitingRoomRequest request = new EnterWaitingRoomRequest();
            ReflectionTestUtils.setField(request, "userId", i);
            ReflectionTestUtils.setField(request, "concertId", CONCERT_ID);
            waitingRoomService.enterQueue(request);
        }
        // MockSystemMetricAdapter의 대기열 크기도 업데이트
        mockSystemMetricAdapter.setWaitingQueueSize(CONCERT_ID, (long) count);
    }
}

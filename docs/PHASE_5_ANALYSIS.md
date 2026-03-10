# T-CORE v2 Phase 5 구현을 위한 핵심 코드 분석

Phase 5 (결제 및 예매 확정 처리) 구현을 위해 필요한 기존 Phase 3 (분산 락)과 Phase 4 (대기열) 코드, 그리고 핵심 도메인 엔티티 및 공통 컴포넌트의 분석 결과입니다.

---

### 1. Domain & Entity (도메인 및 저장소)

**`src/main/java/com/tcore/tcorev2/domain/entity/Seat.java`**
```java
package com.tcore.tcorev2.domain.entity;

import com.tcore.tcorev2.domain.entity.Schedule;
import com.tcore.tcorev2.domain.model.SeatStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA는 기본 생성자가 필요하지만, 무분별한 객체 생성을 막기 위해 protected로 제한
@AllArgsConstructor
@Builder
@Table(name = "seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FetchType.LAZY: 고부하 시스템의 철칙입니다. 
     * EAGER(즉시 로딩)는 불필요한 Join을 발생시켜 DB 부하를 급증시키고 N+1 문제를 야기합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    private String seatNumber; // 좌석 번호 (예: A1, B2)
    private Integer price;

    /**
     * EnumType.STRING: 기본값인 ORDINAL(숫자)은 순서가 바뀌면 데이터가 꼬입니다. 
     * 문자열로 저장하여 데이터 가독성과 안전성을 확보합니다.
     */
    @Enumerated(EnumType.STRING)
    private SeatStatus status; // AVAILABLE, RESERVED, SOLD

    // --- 비즈니스 로직 (Rich Domain Model) ---

    /**
     * 좌석 선점 로직. 
     * 서비스 레이어가 아닌 엔티티 내부에서 상태를 변경함으로써 데이터 정합성 규칙을 강제합니다.
     * AI 에이전트는 나중에 이 메서드의 호출 빈도를 감시하게 됩니다.
     */
    public void reserve() {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("이미 선택되었거나 판매된 좌석입니다.");
        }
        this.status = SeatStatus.RESERVED;
    }

    /**
     * 결제 완료 처리. 
     * 'RESERVED' 상태에서만 결제가 가능하도록 비즈니스 검증을 추가할 수 있습니다.
     */
    public void completePayment() {
        this.status = SeatStatus.SOLD;
    }
}
```

**`src/main/java/com/tcore/tcorev2/domain/entity/Reservation.java`**
```java
package com.tcore.tcorev2.domain.entity;

import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.ReservationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 유저 식별자. 
     * 우선은 간단하게 Long 타입으로 두거나 User 엔티티와 연관관계를 맺습니다.
     */
    private Long userId;

    /**
     * OneToOne: 하나의 예약은 하나의 좌석을 가집니다.
     * (비즈니스 정책에 따라 1:N이 될 수도 있지만, 고도화 단계 전에는 1:1로 단순화)
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    private LocalDateTime reservedAt; // 예약 일시

    @Enumerated(EnumType.STRING)
    private ReservationStatus status; // PENDING(결제대기), CONFIRMED(확정), CANCELLED(취소)

    // --- 생성 메서드 ---
    /**
     * 정적 팩토리 메서드 패턴을 사용하여 객체 생성 로직을 캡슐화합니다.
     * 예약 생성 시점에 현재 시간을 자동으로 기록합니다.
     */
    public static Reservation createReservation(Long userId, Seat seat) {
        Reservation reservation = new Reservation();
        reservation.userId = userId;
        reservation.seat = seat;
        reservation.reservedAt = LocalDateTime.now();
        reservation.status = ReservationStatus.PENDING;
        return reservation;
    }
}
```

**`src/main/java/com/tcore/tcorev2/domain/repository/SeatRepository.java`**
```java
package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Seat;
import com.tcore.tcorev2.domain.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    // 특정 일정의 모든 좌석을 조회 (예매 화면용)
    List<Seat> findByScheduleId(Long scheduleId);
    long countByScheduleIdAndStatus(Long scheduleId, SeatStatus status);
}
```

**`src/main/java/com/tcore/tcorev2/domain/repository/ReservationRepository.java`**
```java
package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
```

---
### 2. Phase 3 Distributed Lock (분산 락 관련)

**`src/main/java/com/tcore/tcorev2/application/facade/ReservationFacade.java`**
```java
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
```

**`src/main/java/com/tcore/tcorev2/infrastructure/config/RedissonConfig.java`**
```java
package com.tcore.tcorev2.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 및 Redisson 분산 락 사용을 위한 설정 클래스
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    private static final String REDISSON_HOST_PREFIX = "redis://";

    /**
     * RedissonClient 빈 등록
     * 단일 서버 모드(Single Server) 설정을 사용합니다.
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(REDISSON_HOST_PREFIX + host + ":" + port)
                .setConnectionMinimumIdleSize(5)  // 최소 유휴 커넥션 수
                .setConnectionPoolSize(20);       // 최대 커넥션 풀 크기

        return Redisson.create(config);
    }
}
```

---
### 3. Phase 4 Waiting Queue (가상 대기열 관련)

**`src/main/java/com/tcore/tcorev2/application/service/RedisWaitingRoomService.java`**
```java
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
}
```

---
### 4. Global (공통 처리)

**`src/main/java/com/tcore/tcorev2/global/common/CommonResponse.java`**
```java
package com.tcore.tcorev2.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>("SUCCESS", "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>("SUCCESS", message, data);
    }

    public static CommonResponse<Void> error(String message) {
        return new CommonResponse<>("ERROR", message, null);
    }
}
```

**[커스텀 예외 처리 구조 관련 참고]**
현재 프로젝트에서는 명시적인 `GlobalExceptionHandler` 또는 커스텀 `*Exception` 클래스가 발견되지 않았습니다. `IllegalStateException`이나 `IllegalArgumentException`과 같은 표준 Java 예외를 사용하여 비즈니스 로직 내에서 직접 예외를 처리하는 것으로 보입니다. 이는 Spring Boot의 기본 예외 처리 메커니즘을 사용하거나, 각 컨트롤러/서비스에서 개별적으로 예외를 처리하고 있음을 시사합니다.

---

이 분석 결과를 통해 Phase 5 구현을 계획하고 기존 코드와 연동하는 데 도움이 되기를 바랍니다. 추가적으로 필요한 정보가 있다면 말씀해주세요.

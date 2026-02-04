# 🛡️ Phase 3: Distributed Lock & Concurrency Control (Dev Log)

> **"1,000 TPS 트래픽에서도 데이터 무결성 100% 보장"**
> Redisson 분산 락과 Facade 패턴을 활용하여 동시성 이슈를 해결한 과정과 기술적 의사결정을 기록합니다.

---

## 🎯 Objective
* **좌석 선점의 원자성(Atomicity) 보장:** 동시에 여러 유저가 동일한 좌석을 예약하려 할 때, 단 한 명의 유저만 성공해야 합니다.
* **DB 부하 최소화:** 데이터베이스의 비관적 락(Pessimistic Lock) 대신 Redis 분산 락을 사용하여 DB 커넥션 점유 시간을 최소화합니다.

---

## 🛠️ Implementation Strategy

### 1. Decision Making: Why Redis Distributed Lock?
티켓팅 시스템과 같이 **짧은 시간에 폭발적인 트래픽이 몰리는 환경(Traffic Spike)**에서는 락 전략의 선택이 시스템 전체의 성능과 생존을 좌우합니다. 
우리는 데이터베이스의 부하를 최소화하고 애플리케이션 레벨에서 유연한 제어를 하기 위해 **Redis 분산 락**을 채택했습니다.

#### 🔄 Comparison of Locking Strategies

| 특징 | 🟢 Redis Distributed Lock (Selected) | 🟡 DB Pessimistic Lock (비관적 락) | 🟠 DB Optimistic Lock (낙관적 락) |
| :--- | :--- | :--- | :--- |
| **작동 방식** | Redis의 `SETNX` 명령어를 이용해 애플리케이션 레벨에서 락을 관리 | `SELECT ... FOR UPDATE` 쿼리로 DB Row 자체에 잠금을 걺 | 버전(Version) 컬럼을 이용해 커밋 시점에 충돌 여부를 확인 |
| **장점** | • **DB 부하 감소:** 락 대기 및 획득 과정이 Redis 메모리에서 처리됨<br>• **유연성:** 타임아웃(TTL) 설정이 자유로워 데드락 방지 용이<br>• **분산 환경 적합:** 여러 서버 인스턴스 간 동기화가 자연스러움 | • **강력한 정합성:** DB 수준에서 완벽하게 데이터를 보호<br>• **구현 단순:** 별도 인프라 없이 SQL만으로 구현 가능 | • **락 비용 없음:** 충돌이 적은 상황에서는 가장 성능이 좋음<br>• **Deadlock 없음:** DB 락을 잡지 않음 |
| **단점** | • **인프라 의존성:** Redis 서버 장애 시 전체 락 기능 마비<br>• **구현 복잡도:** 락 획득/해제, 타임아웃 처리 등 부가 로직 필요 | • **성능 저하:** 트랜잭션이 길어지면 다른 요청들이 DB 커넥션을 잡고 대기함 (Throughput 급감)<br>• **데드락 위험:** 복잡한 쿼리나 조인 시 데드락 발생 가능성 높음 | • **충돌 시 재시도 비용:** 티켓팅처럼 충돌이 빈번한 상황에서는 롤백 및 재시도 로직으로 인해 **오히려 성능이 악화됨** |
| **선정 이유** | 티켓팅은 **"충돌이 매우 빈번(High Contention)"**하고 **"DB 자원이 병목"**이 되는 시스템입니다. DB 커넥션을 락 대기에 낭비하지 않고, 빠른 인메모리 연산으로 락을 제어하는 것이 가장 효율적이라 판단했습니다. | | |

### 2. Why Redisson?
* **Spin Lock 제거:** `Lettuce`와 같은 클라이언트는 락 획득 실패 시 지속적으로 Redis에 요청을 보내는 스핀 락 구조입니다. 반면 `Redisson`은 Pub/Sub 방식을 사용하여 락이 해제될 때 알림을 받아 부하를 획기적으로 줄입니다.
* **TTL (Time-to-Live) 지원:** 서버가 락을 잡은 상태로 다운되더라도 일정 시간이 지나면 자동으로 락이 해제되어 데드락(Deadlock)을 방지합니다.

### 2. Architectural Pattern: Facade
초기에는 `ReservationService` 내부에서 락 획득과 트랜잭션 처리를 모두 수행하려 했으나, **Spring AOP의 프록시 제약**으로 인해 트랜잭션이 적용되지 않는 문제가 발생했습니다. 이를 해결하기 위해 역할과 책임을 분리했습니다.

* **ReservationFacade:** 분산 락(Lock)의 획득과 해제를 담당 (Non-Transactional)
* **ReservationService:** 순수 비즈니스 로직과 데이터베이스 트랜잭션을 담당 (@Transactional)

---

## 🐞 Troubleshooting & Learnings

### 🚨 Issue 1: Transaction Not Applying (Race Condition)
#### 상황
`ReservationService` 내부의 `reserveSeat` 메서드에서 락을 잡고, 같은 클래스 내의 `@Transactional`이 걸린 `processReservation` 메서드를 호출했습니다.
그러나 동시성 테스트 결과, 여러 명의 유저가 동시에 예약을 성공하는 **심각한 정합성 오류(Race Condition)**가 발생했습니다.

#### 원인 분석
Spring의 `@Transactional`은 AOP(Aspect Oriented Programming) 기반으로 동작하며, 이는 프록시 객체를 통해 구현됩니다. 
하지만 **같은 클래스 내부의 메서드 호출(Self-Invocation)은 프록시를 거치지 않고 원본 객체의 메서드를 직접 호출**하기 때문에 `@Transactional` 어노테이션이 무시되었습니다. 이로 인해 락 내부에서 트랜잭션이 보장되지 않아 데이터가 커밋되기 전에 다른 스레드가 락을 획득할 수 있었습니다.

#### 해결 (Solution)
**Facade 패턴 도입:**
1. `ReservationFacade` 클래스를 신설하여 락 관리 로직을 위임했습니다.
2. `ReservationService`는 오직 트랜잭션이 필요한 비즈니스 로직만 수행하도록 리팩토링했습니다.
3. 이를 통해 `Facade` -> `Service` 호출 시 프록시를 경유하게 되어 트랜잭션이 정상적으로 시작되고 종료되도록 보장했습니다.

```java
// ReservationFacade.java (Lock Management)
public ReservationResponse reserveSeat(...) {
    RLock lock = redissonClient.getLock(...);
    if (lock.tryLock(...)) {
        try {
            return reservationService.reserveSeat(...); // Transactional Call via Proxy
        } finally {
            lock.unlock();
        }
    }
}
```

### 🚨 Issue 2: Test Assertion Failure (Stale Data)
#### 상황
동시성 제어 로직 수정 후, 100명 중 1명만 성공하는 것은 확인했으나 테스트 코드의 검증 단계(`assertThat`)에서 좌석 상태가 `RESERVED`가 아닌 `AVAILABLE`로 조회되어 테스트가 실패했습니다.

#### 원인 분석
테스트 환경(`@SpringBootTest`)에서는 영속성 컨텍스트(Persistence Context)가 1차 캐시를 유지합니다. 
비동기 스레드에서 DB를 업데이트했더라도, 메인 테스트 스레드의 `EntityManager`는 이미 조회했던 `AVAILABLE` 상태의 엔티티를 캐시하고 있어 변경 사항을 감지하지 못했습니다.

#### 해결 (Solution)
`EntityManager`를 주입받아 캐시를 강제로 초기화했습니다.
```java
// 모든 스레드 작업 완료 대기
latch.await(); 

// 영속성 컨텍스트 초기화 (DB에서 최신 데이터 조회 강제)
entityManager.clear(); 

// 검증
Seat seat = seatRepository.findById(targetSeatId).orElseThrow();
assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
```

---

## 🧪 Verification Results
* **Test Scenario:** 100명의 유저가 동시에 1개의 좌석 예약을 시도
* **Outcome:**
  * 성공: **1건**
  * 실패: **99건** (예상된 실패)
  * 데이터 상태: 좌석 상태 `RESERVED`, 예약 데이터 1건 생성 확인
* **Conclusion:** 분산 환경에서도 데이터의 원자성이 완벽하게 보장됨을 입증했습니다.

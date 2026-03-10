# 🤖 Phase 5: Reliability, Payment & Self-Healing (Dev Log)

> **"Data Integrity: 외부 장애로부터 시스템의 정합성을 수호하다"**
> 외부 PG 연동 시의 트랜잭션 분리 전략과 CAS 패턴을 이용한 동시성 제어, 그리고 고아 데이터를 정리하는 자가 치유(Self-Healing) 시스템의 구현 과정을 기록합니다.

---

## 🎯 Objective
* **장애 격리 (Fault Isolation):** 느린 외부 API(PG사) 통신이 내부 DB 커넥션 풀을 고갈시키지 않도록 아키텍처를 설계합니다.
* **무중단 정합성 (Atomic Consistency):** 분산 환경에서 별도의 무거운 비관적 락(Pessimistic Lock) 없이도 초과 예매(Overselling)를 원천 차단합니다.
* **복구 자동화 (Self-Healing):** 유저 이탈이나 시스템 장애로 인해 비정상적으로 점유된 좌석을 자동으로 탐색하여 대기열로 재개방합니다.

---

## 🛠️ Implementation Strategy

### 1. Decision Making: Transaction Segregation (Facade Pattern)
결제 과정은 외부망 통신이 포함되어 수 초가 소요됩니다. 이를 DB 트랜잭션 내부에서 처리할 경우, 병목 현상으로 인해 전체 시스템이 마비되는 **Connection Pool Exhaustion**을 초래할 수 있습니다.



#### 🔄 Transaction Boundary Design

| 방식 | 🔴 결제 API를 트랜잭션 내부에 포함 | 🟢 결제 API를 트랜잭션 외부로 분리 (Selected) |
| :--- | :--- | :--- |
| **커넥션 점유** | API 응답이 올 때까지 커넥션을 점유 (긴 대기 시간) | 검증과 확정 시에만 짧게 커넥션 사용 (최소 점유) |
| **시스템 영향** | PG사 응답 지연 시 서버 전체 마비 위험 | PG사 응답 지연이 다른 유저 요청에 영향을 주지 않음 |
| **정합성 관리** | DB 롤백이 용이하나 성능에 치명적 | CAS 패턴과 보상 트랜잭션(환불)으로 정합성 해결 |
| **선정 이유** | 고트래픽 시스템에서 DB 커넥션을 쥐고 외부 I/O를 기다리는 것은 치명적인 리스크입니다. Facade 패턴을 통해 DB 트랜잭션의 생명 주기를 최소화했습니다. | |

### 2. Concurrency Control: CAS (Compare-And-Swap)
스케줄러와 결제 프로세스가 동시에 동일 예약건을 수정하려 할 때 발생하는 **Race Condition**을 방지하기 위해, 원자적 업데이트 쿼리를 활용했습니다.



* **Atomic Update:** `WHERE id = :id AND status = 'PENDING'` 조건을 쿼리에 명시하여, 조회 시점의 상태가 유지될 때만 업데이트를 승인합니다.
* **Optimistic Approach:** 데이터 충돌 가능성이 낮은 구간에서 비관적 락 대신 낙관적 관점의 CAS 패턴을 선택하여 시스템 전체의 처리량(Throughput)을 향상시켰습니다.

---

## 🐞 Troubleshooting & Learnings

### 🚨 Issue 1: Connection Pool Exhaustion (Prevention)
* **상황:** 초기 설계 시 결제 API 호출을 `@Transactional` 내부에 두어 부하 테스트 시 커넥션 고갈 위기 감지.
* **해결:** `PaymentFacade`를 도입하여 **[DB 조회] -> [외부 통신] -> [DB 업데이트]** 순으로 단계를 분리. 각 DB 작업만 개별 트랜잭션으로 처리하여 커넥션 점유 시간을 수 밀리초($ms$) 단위로 단축.

### 🚨 Issue 2: Spring Boot 3.4+ Deprecation (@MockitoBean)
* **상황:** 통합 테스트 작성 중 Spring Boot 3.5.10 환경에서 `@MockBean` 지원 중단 경고 및 테스트 실패.
* **해결:** 최신 테스트 표준에 맞춰 `org.springframework.test.context.bean.override.mockito.MockitoBean`으로 마이그레이션 진행 및 의존성 주입 최적화.

### 🚨 Issue 3: N+1 Problem in Self-Healing Scheduler
* **상황:** 자가 치유 스케줄러가 만료 건을 조회할 때 연관된 좌석(`Seat`) 정보를 개별 쿼리로 조회하는 성능 병목 예측.
* **해결:** **`JOIN FETCH`** 문법을 사용하여 예약과 좌석 데이터를 단일 쿼리로 조회. `status`와 `reservedAt`에 **복합 인덱스**를 생성하여 $O(\log N)$의 성능으로 검색 효율 극대화.



---

## 🧪 Verification Results
* **Test Scenario:**
    1. **Concurrent Access:** 사용자 결제 확정과 스케줄러의 강제 취소가 동시에 발생할 때, CAS 쿼리에 의해 정합성이 유지됨을 확인. -> **PASS**
    2. **Self-Healing:** 유저 이탈 시 5분 후 스케줄러가 해당 좌석을 정확히 탐색하여 `AVAILABLE`로 재개방함을 확인. -> **PASS**
* **Conclusion:** 시스템이 외부 장애 환경에서도 스스로 정합성을 유지하며 가용성을 확보할 수 있는 자가 치유 구조임을 검증했습니다.
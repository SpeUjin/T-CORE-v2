# 🤖 Phase 4: Virtual Waiting Queue & AI Ops (Dev Log)

> **"AIOps: 자율 운영 에이전트가 트래픽을 제어하다"**
> Redis ZSet 기반의 가상 대기열 시스템과 이를 모니터링하고 제어하는 AI Agent의 설계 및 구현 과정을 기록합니다.

---

## 🎯 Objective
* **시스템 보호:** 트래픽 폭주(Traffic Spike) 시 서버가 처리 가능한 수준까지만 유저를 입장시켜 시스템 다운을 방지합니다.
* **공정성 보장:** 선착순 대기열을 통해 먼저 온 유저가 먼저 예매 기회를 갖도록 합니다.
* **자율 운영 (Self-Driving Ops):** 정적 임계치 기반의 단순 제어가 아닌, 시스템 상태(CPU, 대기열 등)를 종합적으로 판단하여 유동적으로 유입량을 조절합니다.

---

## 🛠️ Implementation Strategy

### 1. Decision Making: Why Redis ZSet?
대기열 시스템을 구현하는 방법은 다양하지만, 우리는 성능과 기능을 고려하여 **Redis Sorted Set (ZSet)**을 선택했습니다.

#### 🔄 Comparison of Queue Implementations

| 방식 | 🟢 Redis ZSet (Selected) | 🟡 DB Table | 🟠 Kafka / RabbitMQ |
| :--- | :--- | :--- | :--- |
| **순서 보장** | `Score`(TimeInMillis) 기반으로 완벽한 정렬 제공 (`O(log N)`) | `ORDER BY created_at` 쿼리 필요 (`O(N log N)` or Index) | 파티션 내에서만 순서 보장 (전역 순서 보장 어려움) |
| **내 순번 조회** | `ZRANK` 명령어로 즉시 조회 가능 (`O(log N)`) | `COUNT(*)` 쿼리로 무거운 연산 발생 | 특정 유저의 오프셋(Offset)을 찾기 매우 어려움 |
| **중복 방지** | Key(UserId) 중복 자동 방지 | Unique Index 필요 | 중복 메시지 제거 로직 별도 필요 |
| **선정 이유** | **"내 앞에 몇 명 남았어?"**라는 질문에 가장 빠르고 효율적으로 답할 수 있는 자료구조입니다. DB 부하 없이 수백만 명의 대기열을 실시간으로 관리할 수 있습니다. | | |

### 2. Architectural Pattern: AI-Driven Ops
단순히 "5초마다 100명 입장"과 같은 정적 규칙은 상황(예: 결제 지연으로 인한 DB 커넥션 고갈)에 유연하게 대처하지 못합니다. 이를 해결하기 위해 **AI Agent**를 도입했습니다.

* **Metric Abstraction (SystemMetricPort):** 실제 인프라(Actuator/OS)와 에이전트 로직을 분리하여, 테스트 환경에서는 Mock 데이터를 주입할 수 있도록 유연하게 설계했습니다.
* **Spring AI Function Calling:** LLM이 자연어 판단 결과를 실제 자바 코드 실행(`TrafficControlTools`)으로 연결하는 표준 인터페이스를 활용했습니다.
* **Mock-First Development:** 비용 효율성을 위해 초기 개발 단계에서는 `MockChatModel`과 `MockSystemMetricAdapter`를 사용하여 아키텍처 검증을 선행했습니다.

---

## 🐞 Troubleshooting & Learnings

### 🚨 Issue 1: StackOverflowError (Redis Client Conflict)
#### 상황
`RedisWaitingRoomService` 테스트 중 `activateUsers` 메서드 실행 시 `StackOverflowError`가 발생했습니다.
```text
java.lang.StackOverflowError
	at org.springframework.data.redis.connection.DefaultedRedisConnection.pExpire(...)
    ... (Infinite Recursion)
```

#### 원인 분석
프로젝트에 `spring-boot-starter-data-redis`(Lettuce)와 `redisson-spring-boot-starter`가 공존하면서, **Redis Connection Factory의 프록시 설정이 충돌**했습니다.
특히 Redisson Starter가 Spring의 기본 Redis 설정을 감싸는 과정에서, 특정 명령어(`expire`)가 자기 자신을 계속 호출하는 재귀 루프에 빠지는 호환성 문제가 확인되었습니다.

#### 해결 (Solution)
**"One Client Policy"**를 적용하여 Redis 클라이언트를 통일했습니다.
기존 코드에서 혼용되던 `StringRedisTemplate`(Lettuce 기반)을 모두 제거하고, 이미 분산 락 구현을 위해 설정된 **`RedissonClient`** 로 코드를 리팩토링했습니다.
이를 통해 의존성을 단순화하고 충돌을 근본적으로 해결했습니다.

### 🚨 Issue 2: Mock Metric Synchronization (Test Isolation)
#### 상황
`TrafficControlAgent` 통합 테스트에서 대기열에 유저를 추가했음에도, Agent가 "대기열이 비어있다"고 판단하여 아무런 동작을 하지 않았습니다.

#### 원인 분석
**데이터의 불일치:** `waitingRoomService`는 **실제 Redis**에 데이터를 썼지만, `TrafficControlAgent`가 바라보는 `MockSystemMetricAdapter`는 **메모리 상의 가짜 데이터**만 가지고 있었습니다. 테스트 환경에서 이 두 저장소 간의 자동 동기화가 이루어지지 않았습니다.

#### 해결 (Solution)
테스트 코드 및 `TrafficControlTools` 내부에서 실제 동작(Redis 조작)이 일어날 때마다 `MockSystemMetricAdapter`의 상태도 함께 업데이트해주는 **수동 동기화 로직**을 추가하여 시뮬레이션의 정확도를 확보했습니다.

---

## 🧪 Verification Results
* **Test Scenario:**
    1. **Low CPU (20%):** Agent가 적극적으로 유저 활성화 (20명) -> **PASS**
    2. **High CPU (80%):** Agent가 시스템 보호를 위해 활성화 축소 (5명) -> **PASS**
* **Conclusion:** AI 에이전트가 시스템의 상태를 인지하고, 상황에 맞는 의사결정(Traffic Throttling)을 자율적으로 수행함을 확인했습니다.

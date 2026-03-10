### 🚀 Pull Request: Virtual Waiting Queue & AI Ops (Phase 4)

**Base Branch:** `develop`
**Compare Branch:** `feature/waiting-room`

---

### 📝 Description
본 PR은 대규모 트래픽 처리를 위한 **가상 대기열(Virtual Waiting Queue)** 시스템과 이를 자율적으로 제어하는 **AI 기반 트래픽 제어 에이전트(AI Traffic Control Agent)** 의 구현을 포함합니다.
Redis의 고성능 자료구조를 활용하여 대기열 진입/이탈을 효율적으로 처리하며, Spring AI 기반의 에이전트가 시스템 부하(Mock)에 따라 유입량을 동적으로 조절합니다.

### ✨ Key Features
1.  **Redis ZSet 기반 가상 대기열 (RedisWaitingRoomService)**
    *   `Waiting Queue (ZSet)`: `Score`를 타임스탬프로 사용하여 선착순 대기열 구현 (O(log N)).
    *   `Active Queue (Set)`: 입장 허용된 유저 관리 및 만료 시간(TTL) 적용.
    *   `Redisson Client 통합`: Lettuce와의 혼용으로 인한 `StackOverflowError` 해결 및 의존성 단일화.

2.  **AI Traffic Control Agent (AIOps)**
    *   **Architecture:** `Metric Provider` (상황 인지) -> `AI Agent` (판단) -> `Traffic Control Tool` (실행) 구조 확립.
    *   **Spring AI Function Calling:** LLM이 시스템 제어 도구(`activateUsers`, `getQueueMetrics`)를 호출할 수 있도록 추상화.
    *   **Mock Strategy:** 개발 단계의 비용 효율성을 위해 `MockChatModel`과 `MockSystemMetricAdapter`를 사용하여 로직 검증.

3.  **API Endpoints**
    *   `POST /api/v1/waiting-room/enter`: 대기열 진입 요청.
    *   `GET /api/v1/waiting-room/status`: 현재 나의 대기 순번 및 예상 대기 시간 조회.

### 🛠️ Technical Decisions & Trouble Shooting
*   **Redis Client Conflict 해결:** `redisson-spring-boot-starter`와 `spring-boot-starter-data-redis` 충돌로 인한 무한 재귀 호출 문제를 `RedissonClient` 단일 사용 정책으로 해결.
*   **Test Isolation & Sync:** 통합 테스트 시 Mock Metric과 실제 Redis 데이터 간의 불일치 문제를 해결하기 위해 수동 동기화 로직 적용.
*   *상세 내용은 `docs/PHASE_4_DEV_LOG.md` 문서를 참고해 주세요.*

### ✅ Verification
*   **통합 테스트 (`RedisWaitingRoomServiceTest`):**
    *   [x] 대기열 진입 및 순번 부여 검증 (ZSet Rank 확인).
    *   [x] 유저 활성화(Activate) 시 상태 변경 및 대기열 이탈 검증.
*   **에이전트 로직 테스트 (`TrafficControlAgentTest`):**
    *   [x] Low CPU 시 유입량 증가 동작 확인.
    *   [x] High CPU 시 유입량 감소(Throttling) 동작 확인.
    
---

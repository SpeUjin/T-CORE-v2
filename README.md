# 🎫 T-CORE v2
> **AI-Managed High-Traffic Ticketing Engine**
> 자율 운영 AI 에이전트를 결합한 고성능·고가용성 티켓팅 시스템

---

## 🚀 Project Vision
**"단순한 기능 구현을 넘어, 엔지니어링으로 대규모 트래픽의 한계를 극복합니다."**

T-CORE는 인기 콘서트 예매 시 발생하는 급격한 트래픽 폭주(Traffic Spike) 상황에서도 시스템의 안정성을 유지하고, 데이터 정합성을 100% 보장하는 것을 목표로 합니다. 특히, 현대적인 **AIOps** 개념을 도입하여 AI 에이전트가 실시간으로 시스템을 모니터링하고 장애에 자율적으로 대응하는 차세대 백엔드 아키텍처를 지향합니다.

---

## 🛠 Tech Stack & Decision Rationale

본 프로젝트는 단순한 기술의 유행을 따르는 것이 아니라, **성능(Performance), 확장성(Scalability), 운영 효율성(Efficiency)**이라는 엔지니어링 원칙에 따라 최적의 기술 버전을 선정했습니다.

### 1. Core Engine: Java 21 & Spring Boot 3.5.10
* **Java 21:** Spring Boot 3.x의 베이스라인인 Java 21을 채택했습니다. Virtual Threads를 통해 수만 개의 경량 스레드 간 데이터를 더 안전하고 가볍게 공유하여 고부하 I/O 상황에서의 리소스 효율을 극대화했습니다.
* **Spring Boot 3.5.10:** Jakarta EE 10을 기반으로 하는 최신 마이너 버전입니다. 프레임워크 차원에서 Spring AI 1.0.0-M5과의 완벽한 통합을 지원하며, 런타임 최적화를 통해 티켓팅과 같은 트래픽 Spike 상황에서 최소한의 오버헤드로 최대의 성능을 이끌어냅니다.

### 2. Intelligent Ops: Spring AI 1.0.0-M5 (Agentic Framework)
* **Spring AI 1.0.0-M5:** Spring Boot 3.5.10과 밀접하게 설계된 AI 프레임워크입니다.
* **선정 이유:** 외부 라이브러리(LangChain4j 등)에 대한 의존성을 최소화하고, **Spring Actuator**가 수집하는 실시간 메트릭(CPU, 대기열 상태 등)을 AI 에이전트가 직접 관찰(Observability)하고 제어할 수 있는 표준 인터페이스를 제공하기 때문입니다. 이를 통해 자바 네이티브한 AIOps 환경을 구축했습니다.

### 3. Storage & Concurrency: Redis 7.4 & MariaDB 11.4 (LTS)
* **Redis 7.4 (Redisson 3.42+):** 초과 예약(Overselling) 방지와 가상 대기열 구현을 위해 선정했습니다. Redis 7.4의 개선된 인덱싱 기능은 실시간 좌석 조회를 가속화하며, **Redisson**의 Pub/Sub 기반 분산 락을 통해 스핀 락(Spin Lock) 없는 효율적인 동시성 제어를 보장합니다.
* **MariaDB 11.4 (LTS):** 11.x 시리즈의 장기 지원(LTS) 버전입니다. 대규모 트래픽 하에서의 트랜잭션 안정성이 검증되었으며, 특히 향후 에이전트의 예매 패턴 분석 및 추천에 필요한 **벡터 검색(Vector Search)** 기능을 내장하고 있어 데이터 계층의 확장성을 확보했습니다.

---

### 📊 Technology Stack Summary

| 분류 | 기술 | 버전 | 핵심 역할 |
| :--- | :--- | :--- | :--- |
| **Language** | Java | **21** | 고성능 가상 스레드 및 데이터 안정성 |
| **Framework** | Spring Boot | **3.5.10** | 시스템 엔진 및 Jakarta EE 10 표준 준수 |
| **AI Agent** | Spring AI | **1.0.0-M5** | 자율 시스템 모니터링 및 자동 제어 |
| **Lock/Cache** | Redis | **7.4** | 분산 락 및 가상 대기열(ZSET) 관리 |
| **Database** | MariaDB | **11.4 (LTS)** | 영속성 데이터 관리 및 벡터 데이터 지원 |

---

## 🏗 System Architecture
```mermaid
graph TD
    %% 사용자 및 로드밸런서
    User((User Client)) --> LB[Nginx / Load Balancer]
    
    %% 서버 및 로직
    subgraph "Spring Boot Server (Java 21 Virtual Threads)"
        LB --> API[REST Controller]
        API --> VWR[Virtual Waiting Room Service]
        VWR --> DL[Distributed Lock Manager]
        DL --> RS[Reservation Service]
    end

    %% AI 레이어
    subgraph "AI Observability Layer"
        Agent((AI Monitoring Agent))
        Actuator[Spring Boot Actuator] --> Agent
        Agent -- "Tool Calling" --> VWR
        Agent -- "Decision" --> DL
    end

    %% 데이터 저장소
    RS --> MariaDB[(MariaDB 11.4)]
    VWR -- "Queue/Token" --> Redis
    DL -- "Redisson Lock" --> Redis[(Redis 7.4)]
    
    %% AI 외부 연결
    Agent -.-> LLM[OpenAI / GPT-4o]
```
### 💳 Phase 5: Payment & Reservation Flow (결제 및 예매 확정 아키텍처)

티켓팅 시스템의 가장 중요한 구간인 결제 및 예매 확정 단계의 비즈니스 흐름입니다. 외부 PG사 연동 시 발생할 수 있는 네트워크 지연 및 타임아웃 상황에서도 **데이터 정합성**을 보장하고, **안전한 좌석 재개방(보상 트랜잭션)**이 이루어지도록 설계했습니다.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant API as API Server
    participant RS as Reservation Service
    participant Redis as Redis (Lock/Queue)
    participant DB as MariaDB
    participant PG as External PG (Mock)

    User->>API: 결제 요청 (예약 정보 및 결제 수단)
    API->>RS: 결제 처리 위임
    RS->>Redis: 좌석 Lock 및 Active Queue 유효성 검증
    
    alt Lock 만료 또는 Queue 이탈
        Redis-->>RS: Invalid / Expired
        RS-->>API: Timeout Exception
        API-->>User: 결제 시간 초과 안내 (메인 이동)
    else Lock 유효 (Valid)
        Redis-->>RS: Valid
        RS->>DB: 예약 상태 변경 [PENDING_PAYMENT]
        
        Note over RS, PG: 트랜잭션 분리 구간 (Network I/O 방어)
        RS->>PG: 외부 결제 승인 요청 (Mock)
        
        alt 결제 성공 (Happy Path)
            PG-->>RS: 결제 승인 완료
            RS->>DB: 예약 상태 변경 [CONFIRMED]
            RS->>Redis: 좌석 Lock 해제 & Active Queue 이탈 (예매 완료)
            RS-->>API: 예매 완료 정보 반환
            API-->>User: 티켓 발급 및 예매 완료 화면
        else 결제 실패 / 오류
            PG-->>RS: 결제 실패 (Fail / Error)
            RS->>DB: 예약 상태 변경 [CANCELLED]
            RS->>Redis: 좌석 Lock 즉시 해제 (좌석 재개방)
            RS-->>API: 예매 실패 에러 반환
            API-->>User: 결제 실패 안내 (좌석 선택 재이동)
        end
    end

    %% Safety Net (보상 트랜잭션)
    loop Every 1 Minute (Scheduler)
        RS->>DB: 만료된 [PENDING_PAYMENT] 상태 조회
        RS->>DB: 상태 [CANCELLED] 일괄 업데이트
        RS->>Redis: 점유된 좌석 Lock 강제 해제 (Safety Net)
    end
```
#### 💡 Key Engineering Points in Phase 5
1. **상태 기반 결제 대기 (PENDING_PAYMENT):** 외부 API 연동 중 서버 장애가 발생하더라도 결제 상태를 추적할 수 있도록 임시 상태를 도입했습니다.
2. **트랜잭션 분리 (Transaction Segregation):** 외부 PG사 호출(Network I/O) 구간을 DB 트랜잭션(`@Transactional`)에서 분리하여, 결제 지연으로 인한 DB 커넥션 풀 고갈(Connection Pool Exhaustion)을 방지합니다.
3. **Safety Net 스케줄러 (보상 트랜잭션):** 브라우저 종료, 네트워크 단절 등으로 결제 상태가 고착화된 경우, 백그라운드 스케줄러가 주기적으로 만료된 예약을 찾아 취소 처리하고 Redis 락을 강제로 해제하여 좌석을 안전하게 대기열로 재개방합니다.
---
## 📂 Directory Structure

본 프로젝트는 유지보수성과 확장성을 극대화하기 위해 **계층형 아키텍처(Layered Architecture)**를 채택하였으며, 각 레이어의 책임을 명확히 분리했습니다.

```text
src/main/java/com/tcore/tcorev2/
├── api/                # [Interface Layer] 외부 요청(HTTP/REST) 진입점
│   ├── controller/     # API 엔드포인트 정의
│   └── dto/            # 데이터 전송 객체 (Request/Response 분리)
├── application/        # [Application Layer] 비즈니스 로직 조립 및 트랜잭션 관리
│   └── service/        # 비즈니스 흐름 제어 (Use Case 구현)
├── domain/             # [Domain Layer] 핵심 비즈니스 규칙 및 도메인 모델 (Pure Logic)
│   ├── entity/         # JPA 엔티티 (핵심 데이터 모델)
│   ├── repository/     # 데이터 접근 인터페이스
│   └── model/          # 도메인 전용 Enum 및 상수
├── infrastructure/     # [Infrastructure Layer] 외부 기술 및 라이브러리 연동
│   ├── ai/             # Spring AI 기반 자율 운영 에이전트 구현체
│   ├── redis/          # Redisson 분산 락 및 대기열(ZSET) 설정
│   └── config/         # DB, Security 등 전역 설정
└── global/             # [Global] 프로젝트 전역 공통 모듈
    ├── error/          # 공통 예외 처리 (GlobalExceptionHandler)
    ├── util/           # 공통 유틸리티 클래스
    └── common/         # 공통 Response 형식 및 상수
```
---
## 💡 Key Engineering Challenges
### 1. 분산 환경에서의 레이스 컨디션 해결
* 다중 서버 환경에서 발생하는 좌석 선점 문제를 해결하기 위해 **Redis 분산 락**을 도입하여 1,000 TPS 이상의 환경에서도 데이터 오차율 0%를 달성하고자 했습니다.

### 2. 가상 대기열을 이용한 DB 부하 분산
* 모든 요청이 직접 DB로 인입되지 않도록 **Redis Sorted Set** 기반의 가상 대기열(Virtual Waiting Room)을 구축하여, 시스템 가용 범위 내에서만 트래픽을 순차적으로 처리합니다.

### 3. AI 기반 자율 장애 대응 (Self-Healing)
* 정적 임계치 기반의 모니터링 한계를 극복하기 위해 AI 에이전트를 도입했습니다. 에이전트는 실시간 지표를 분석하여 비정상 패턴을 감지하면 즉시 **Rate Limiting** 수치를 조정하거나 이상 IP를 차단하는 도구를 스스로 실행합니다.

---
## 🗺️ Project Roadmap

티켓팅 시스템의 핵심 기능을 단계별로 구현하며, 각 단계마다 성능 최적화와 정합성 검증을 병행합니다.

### ✅ Phase 1: Infrastructure & Domain Modeling (Completed)
- [x] 기술 스택 선정 및 프로젝트 환경 설정 (Java 21, Spring Boot 3.5.10)
- [x] 핵심 도메인 모델링 (Concert, Schedule, Seat, Reservation)
- [x] Docker 기반 MariaDB 및 Redis 인프라 구축

### 🔄 Phase 2: Concert Information API (In Progress)
- [x] 전체 공연 목록 조회 API 구현
- [x] 특정 공연의 상세 일정 및 실시간 잔여 좌석 조회 구현
- [x] 레이어드 아키텍처 리팩토링 및 테스트 코드(MockMvc, Mockito) 작성
- [x] 개발 브랜치(`feature/concert-search`) PR 및 `develop` 병합

### 📅 Phase 3: Distributed Lock Reservation (Completed)
- [x] Redisson 기반 분산 락(Distributed Lock) 환경 설정
- [x] 좌석 선택 및 임시 예약 로직 구현 (초과 예약 방지)
- [x] 동시성 테스트를 통한 데이터 정합성 검증

### 🚀 Phase 4: Virtual Waiting Queue & AI Ops (Completed)
- [x] Redis ZSet 기반의 가상 대기열(Waiting Queue) 시스템 구축
- [x] Spring AI를 활용한 실시간 트래픽 모니터링 및 대기열 자동 제어
- [x] 트래픽 Spike 상황에서의 시스템 부하 테스트 (Mock 시뮬레이션 검증)

### 💳 Phase 5: Payment & Final Confirmation (Planned)
- [ ] 결제 연동 시뮬레이션 및 예매 확정 처리
- [ ] 예매 취소 및 좌석 재개방 로직 구현
- [ ] 전체 비즈니스 흐름 최종 통합 테스트

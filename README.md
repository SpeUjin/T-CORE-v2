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

### 1. Core Engine: Java 25 (LTS) & Spring Boot 4.0.2
* **Java 25 (LTS):** Spring Boot 4.x의 베이스라인인 Java 25를 채택했습니다. 이전 LTS(21) 대비 **Virtual Threads**의 스케줄링 성능이 개선되었으며, **Scoped Values**를 통해 수만 개의 경량 스레드 간 데이터를 더 안전하고 가볍게 공유하여 고부하 I/O 상황에서의 리소스 효율을 극대화했습니다.
* **Spring Boot 4.0.2:** Jakarta EE 11을 기반으로 하는 최신 메이저 버전입니다. 프레임워크 차원에서 **Spring AI 2.0**과의 완벽한 통합을 지원하며, 런타임 최적화를 통해 티켓팅과 같은 트래픽 Spike 상황에서 최소한의 오버헤드로 최대의 성능을 이끌어냅니다.

### 2. Intelligent Ops: Spring AI 2.0.0 (Agentic Framework)
* **Spring AI 2.0.0:** Spring Boot 4.0.2와 밀접하게 설계된 차세대 AI 프레임워크입니다.
* **선정 이유:** 외부 라이브러리(LangChain4j 등)에 대한 의존성을 최소화하고, **Spring Actuator**가 수집하는 실시간 메트릭(CPU, 대기열 상태 등)을 AI 에이전트가 직접 관찰(Observability)하고 제어할 수 있는 표준 인터페이스를 제공하기 때문입니다. 이를 통해 자바 네이티브한 AIOps 환경을 구축했습니다.

### 3. Storage & Concurrency: Redis 7.4 & MariaDB 11.4 (LTS)
* **Redis 7.4 (Redisson 3.42+):** 초과 예약(Overselling) 방지와 가상 대기열 구현을 위해 선정했습니다. Redis 7.4의 개선된 인덱싱 기능은 실시간 좌석 조회를 가속화하며, **Redisson**의 Pub/Sub 기반 분산 락을 통해 스핀 락(Spin Lock) 없는 효율적인 동시성 제어를 보장합니다.
* **MariaDB 11.4 (LTS):** 11.x 시리즈의 장기 지원(LTS) 버전입니다. 대규모 트래픽 하에서의 트랜잭션 안정성이 검증되었으며, 특히 향후 에이전트의 예매 패턴 분석 및 추천에 필요한 **벡터 검색(Vector Search)** 기능을 내장하고 있어 데이터 계층의 확장성을 확보했습니다.

---

### 📊 Technology Stack Summary

| 분류 | 기술 | 버전 | 핵심 역할 |
| :--- | :--- | :--- | :--- |
| **Language** | Java | **25 (LTS)** | 고성능 가상 스레드 및 데이터 안정성 |
| **Framework** | Spring Boot | **4.0.2** | 시스템 엔진 및 Jakarta EE 11 표준 준수 |
| **AI Agent** | Spring AI | **2.0.0** | 자율 시스템 모니터링 및 자동 제어 |
| **Lock/Cache** | Redis | **7.4** | 분산 락 및 가상 대기열(ZSET) 관리 |
| **Database** | MariaDB | **11.4 (LTS)** | 영속성 데이터 관리 및 벡터 데이터 지원 |

---

## 🏗 System Architecture
```mermaid
graph TD
    %% 사용자 및 로드밸런서
    User((User Client)) --> LB[Nginx / Load Balancer]
    
    %% 서버 및 로직
    subgraph "Spring Boot Server (Java 25 Virtual Threads)"
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
- [ ] 개발 브랜치(`feature/concert-search`) PR 및 `develop` 병합

### 📅 Phase 3: Distributed Lock Reservation (Planned)
- [ ] Redisson 기반 분산 락(Distributed Lock) 환경 설정
- [ ] 좌석 선택 및 임시 예약 로직 구현 (초과 예약 방지)
- [ ] 동시성 테스트를 통한 데이터 정합성 검증

### 🚀 Phase 4: Virtual Waiting Queue & AI Ops (Planned)
- [ ] Redis ZSet 기반의 가상 대기열(Waiting Queue) 시스템 구축
- [ ] Spring AI를 활용한 실시간 트래픽 모니터링 및 대기열 자동 제어
- [ ] 트래픽 Spike 상황에서의 시스템 부하 테스트

### 💳 Phase 5: Payment & Final Confirmation (Planned)
- [ ] 결제 연동 시뮬레이션 및 예매 확정 처리
- [ ] 예매 취소 및 좌석 재개방 로직 구현
- [ ] 전체 비즈니스 흐름 최종 통합 테스트

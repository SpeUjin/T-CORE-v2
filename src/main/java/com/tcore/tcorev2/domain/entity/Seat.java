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
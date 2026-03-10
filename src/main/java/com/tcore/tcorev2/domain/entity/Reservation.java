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
@Table(name = "reservations", indexes = {
        // status와 reservedAt을 묶어서 조회 속도를 극대화하는 복합 인덱스
        @Index(name = "idx_status_reserved_at", columnList = "status, reservedAt")
})
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

    public void confirm() { this.status = ReservationStatus.CONFIRMED; }
    public void cancel() { this.status = ReservationStatus.CANCELLED; }
}
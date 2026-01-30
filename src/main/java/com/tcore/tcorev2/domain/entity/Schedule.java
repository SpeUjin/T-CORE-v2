package com.tcore.tcorev2.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "schedules")
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id")
    private Concert concert;

    private LocalDateTime startTime; // 공연 시작 시간

    /**
     * CascadeType.ALL: 일정이 삭제되면 해당 좌석 정보도 함께 관리되도록 설정.
     * 하지만 실제 운영에서는 부작용을 막기 위해 꼼꼼한 관리가 필요합니다.
     */
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL)
    private List<Seat> seats = new ArrayList<>();
}
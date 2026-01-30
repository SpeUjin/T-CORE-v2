package com.tcore.tcorev2.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "concerts")
public class Concert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;      // 공연 제목
    private String artist;     // 아티스트 명

    @Column(columnDefinition = "TEXT")
    private String description; // 공연 상세 설명 (대용량 텍스트 대비)

    /**
     * 일대다 관계 설정 (Concert : Schedule = 1 : N)
     * mappedBy: 양방향 관계의 주인이 아님을 명시 (Schedule.concert가 주인)
     * cascade: 공연이 삭제되면 하위 일정들도 관리되도록 설정
     */
    @Builder.Default // 빌더 사용 시 리스트 초기화를 유지하기 위해 필요합니다.
    @OneToMany(mappedBy = "concert", cascade = CascadeType.ALL)
    private List<Schedule> schedules = new ArrayList<>();
}
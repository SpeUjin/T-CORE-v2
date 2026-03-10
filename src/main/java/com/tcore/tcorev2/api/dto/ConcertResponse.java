package com.tcore.tcorev2.api.dto;

import lombok.Builder;
import lombok.Getter;

// 공연 목록 응답
@Getter @Builder
public class ConcertResponse {
    private Long id;
    private String title;
    private String artist;
}

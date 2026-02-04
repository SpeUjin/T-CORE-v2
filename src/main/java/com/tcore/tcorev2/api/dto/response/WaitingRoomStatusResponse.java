package com.tcore.tcorev2.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WaitingRoomStatusResponse {
    private Long userId;
    private Long rank; // 현재 대기 순번
    private String status; // WAITING, ACTIVE, EXPIRED
    private Long estimatedWaitSeconds; // 예상 대기 시간 (초)
}

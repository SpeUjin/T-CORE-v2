package com.tcore.tcorev2.api.dto;

import lombok.Getter;
import lombok.Builder;
import java.time.LocalDateTime;

@Getter
@Builder
public class ScheduleResponse {
    private Long id;
    private LocalDateTime startTime;
    private long availableSeatCount;
}

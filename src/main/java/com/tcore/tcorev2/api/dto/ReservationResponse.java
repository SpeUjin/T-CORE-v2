package com.tcore.tcorev2.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ReservationResponse {
    private Long reservationId;
    private String seatNumber;
    private String status;
    private LocalDateTime reservedAt;
}
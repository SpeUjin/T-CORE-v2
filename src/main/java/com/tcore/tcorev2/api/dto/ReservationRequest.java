package com.tcore.tcorev2.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좌석 예매 요청 DTO
 */
@Getter
@NoArgsConstructor
public class ReservationRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotNull(message = "좌석 ID는 필수입니다.")
    private Long seatId;
}
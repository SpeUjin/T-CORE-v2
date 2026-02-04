package com.tcore.tcorev2.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EnterWaitingRoomRequest {
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotNull(message = "콘서트 ID는 필수입니다.")
    private Long concertId;
}

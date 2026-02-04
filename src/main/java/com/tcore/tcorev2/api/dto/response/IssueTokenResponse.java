package com.tcore.tcorev2.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IssueTokenResponse {
    private String token;
    private Long validSeconds; // 토큰 유효 시간 (초)
}

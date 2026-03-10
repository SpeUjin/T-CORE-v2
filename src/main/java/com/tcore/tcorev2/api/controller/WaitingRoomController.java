package com.tcore.tcorev2.api.controller;

import com.tcore.tcorev2.api.dto.request.EnterWaitingRoomRequest;
import com.tcore.tcorev2.api.dto.response.WaitingRoomStatusResponse;
import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import com.tcore.tcorev2.global.common.CommonResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull; // Missing import added
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/waiting-room")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final RedisWaitingRoomService waitingRoomService;

    /**
     * 대기열 진입 API
     * POST /api/v1/waiting-room/enter
     */
    @PostMapping("/enter")
    public ResponseEntity<CommonResponse<String>> enterWaitingRoom(@Valid @RequestBody EnterWaitingRoomRequest request) { // Changed to CommonResponse<String>
        waitingRoomService.enterQueue(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success("대기열에 성공적으로 진입했습니다."));
    }

    /**
     * 대기열 순번 및 상태 조회 API
     * GET /api/v1/waiting-room/status
     */
    @GetMapping("/status")
    public ResponseEntity<CommonResponse<WaitingRoomStatusResponse>> getWaitingStatus(
            @RequestParam @NotNull(message = "사용자 ID는 필수입니다.") Long userId,
            @RequestParam @NotNull(message = "콘서트 ID는 필수입니다.") Long concertId) {

        WaitingRoomStatusResponse response = waitingRoomService.getWaitingStatus(userId, concertId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(response));
    }
}

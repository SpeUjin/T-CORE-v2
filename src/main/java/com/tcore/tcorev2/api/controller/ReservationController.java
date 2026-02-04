package com.tcore.tcorev2.api.controller;

import com.tcore.tcorev2.api.dto.ReservationRequest;
import com.tcore.tcorev2.api.dto.ReservationResponse;
import com.tcore.tcorev2.application.facade.ReservationFacade;
import com.tcore.tcorev2.global.common.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationFacade reservationFacade;

    /**
     * 좌석 예약 API
     * POST /api/v1/reservations
     */
    @PostMapping
    public ResponseEntity<CommonResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody ReservationRequest request) {
        
        ReservationResponse response = reservationFacade.reserveSeat(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response));
    }
}

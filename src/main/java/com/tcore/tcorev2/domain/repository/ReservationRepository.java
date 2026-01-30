package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}

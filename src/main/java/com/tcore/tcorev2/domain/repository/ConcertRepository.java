package com.tcore.tcorev2.domain.repository;

import com.tcore.tcorev2.domain.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
}

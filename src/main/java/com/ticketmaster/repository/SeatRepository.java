package com.ticketmaster.repository;

import com.ticketmaster.entity.EventEntity;
import com.ticketmaster.entity.SeatEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<SeatEntity, Long> {

    Page<SeatEntity> findByEvent(EventEntity event, Pageable pageable);
}

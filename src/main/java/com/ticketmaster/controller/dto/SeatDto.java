package com.ticketmaster.controller.dto;

import com.ticketmaster.entity.SeatEntity;

public record SeatDto(Long seatId, String name, String status) {

    public static SeatDto fromEntity(SeatEntity entity) {
        return new SeatDto(entity.getId(), entity.getName(), entity.getStatus().name());
    }
}

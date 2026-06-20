package com.ticketmaster.controller.dto;

import com.ticketmaster.entity.EventEntity;

public record EventDto(Long id, String name, String description) {

    public static EventDto fromEntity(EventEntity entity) {
        return new EventDto(entity.getId(), entity.getName(), entity.getDescription());
    }
}

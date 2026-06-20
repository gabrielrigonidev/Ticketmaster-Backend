package com.ticketmaster.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventDto(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Valid EventSettingDto settings) {
}

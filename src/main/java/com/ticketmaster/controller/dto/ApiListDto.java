package com.ticketmaster.controller.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record ApiListDto<T>(List<T> data, PaginationDto pagination) {

    public static <E, T> ApiListDto<T> from(Page<E> page, Function<E, T> mapper) {
        List<T> data = page.getContent().stream().map(mapper).toList();
        PaginationDto pagination = new PaginationDto(
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements()
        );
        return new ApiListDto<>(data, pagination);
    }
}

package com.ticketmaster.controller.dto;

public record PaginationDto(int page, int pageSize, int totalPages, long totalItems) {
}

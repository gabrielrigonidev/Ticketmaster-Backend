package com.ticketmaster.exception;

import java.util.List;

public record ExceptionResponse(
        String type,
        String title,
        String detail,
        Integer status,
        List<InvalidParamResponse> invalidParams) {
}

package com.ticketmaster.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TicketMasterException.class)
    public ResponseEntity<ExceptionResponse> handleTicketMasterException(TicketMasterException ex) {
        ExceptionResponse body = new ExceptionResponse(
                "about:blank",
                ex.getTitle(),
                ex.getDetail(),
                ex.getStatus(),
                null);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<InvalidParamResponse> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError e) -> new InvalidParamResponse(e.getField(), e.getDefaultMessage()))
                .toList();
        ExceptionResponse body = new ExceptionResponse(
                "about:blank",
                "Validation error",
                "One or more fields are invalid.",
                400,
                invalidParams);
        return ResponseEntity.badRequest().body(body);
    }
}

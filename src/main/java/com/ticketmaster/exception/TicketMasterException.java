package com.ticketmaster.exception;

import lombok.Getter;

@Getter
public class TicketMasterException extends RuntimeException {

    private final String title;
    private final String detail;
    private final int status;

    public TicketMasterException(String title, String detail, int status) {
        super(detail);
        this.title = title;
        this.detail = detail;
        this.status = status;
    }
}

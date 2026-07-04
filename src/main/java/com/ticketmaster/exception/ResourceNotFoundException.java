package com.ticketmaster.exception;

public class ResourceNotFoundException extends TicketMasterException {

    public ResourceNotFoundException(String title, String detail) {
        super(title, detail, 422);
    }
}

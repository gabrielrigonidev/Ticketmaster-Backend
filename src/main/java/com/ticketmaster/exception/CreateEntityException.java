package com.ticketmaster.exception;

public class CreateEntityException extends TicketMasterException {

    public CreateEntityException(String title, String detail) {
        super(title, detail, 422);
    }
}

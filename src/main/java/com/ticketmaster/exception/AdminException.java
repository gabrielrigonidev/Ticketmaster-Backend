package com.ticketmaster.exception;

public class AdminException extends TicketMasterException {

    public AdminException() {
        super("Admin creation exception",
                "Admin user can only be created when there are no users in the system.",
                422);
    }
}

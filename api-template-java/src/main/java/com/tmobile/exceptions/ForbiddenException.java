package com.tmobile.exceptions;

public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super("Forbidden", message);
    }
}

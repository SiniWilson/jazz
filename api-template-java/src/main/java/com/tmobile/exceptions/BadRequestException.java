package com.tmobile.exceptions;

public class BadRequestException extends BaseException {

    public BadRequestException(String message) {
        super("BadRequest", message);
    }
}

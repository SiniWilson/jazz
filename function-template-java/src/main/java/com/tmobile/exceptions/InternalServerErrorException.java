package com.tmobile.exceptions;

public class InternalServerErrorException extends BaseException {
	
	public InternalServerErrorException(String message) {
		super("InternalServerError", message);
	}

}

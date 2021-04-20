package com.tmobile.exceptions;

public class NotFoundException extends BaseException {
	
	public NotFoundException(String message) {
		super("NotFound", message);
	}

}

package com.ludogoriesoft.sigmatherm.exception;

import org.springframework.http.HttpStatus;

public class ObjectExistsException extends ApiException {

    public ObjectExistsException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

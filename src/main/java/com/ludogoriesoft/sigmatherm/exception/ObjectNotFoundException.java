package com.ludogoriesoft.sigmatherm.exception;

import org.springframework.http.HttpStatus;

public class ObjectNotFoundException extends ApiException{
    public ObjectNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}

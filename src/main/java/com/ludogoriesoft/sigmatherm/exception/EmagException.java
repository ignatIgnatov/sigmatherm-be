package com.ludogoriesoft.sigmatherm.exception;

import org.springframework.http.HttpStatus;

public class EmagException extends ApiException{
    public EmagException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}

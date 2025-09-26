package com.ludogoriesoft.sigmatherm.exception;

import org.springframework.http.HttpStatus;

public class BolApiException extends ApiException {
    public BolApiException(String message, HttpStatus status) {
        super(message, status);
    }
}

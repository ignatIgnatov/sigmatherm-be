package com.ludogoriesoft.sigmatherm.exception;

import com.ludogoriesoft.sigmatherm.dto.response.ExceptionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleBadEnum(Exception ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ExceptionResponse> handleApiExceptions(ApiException exception) {
        ExceptionResponse apiException = ApiExceptionParser.parseException(exception);
        return ResponseEntity.status(apiException.getStatus()).body(apiException);
    }
}

package com.ludogoriesoft.sigmatherm.exception;

import com.ludogoriesoft.sigmatherm.dto.response.ExceptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class ApiExceptionParser {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionParser.class);

    public static ExceptionResponse parseException(ApiException exception) {
        logger.error("Error occurred: {} ", exception.getMessage(), exception);
        return ExceptionResponse
                .builder()
                .dateTime(LocalDateTime.now())
                .message(exception.getMessage())
                .status(exception.getStatus())
                .statusCode(exception.getStatusCode())
                .build();
    }
}

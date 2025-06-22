package com.ludogoriesoft.sigmatherm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionResponse {
    private String message;
    private LocalDateTime dateTime;
    private HttpStatus status;
    private Integer statusCode;
    private Map<String, String> validationErrors;
}

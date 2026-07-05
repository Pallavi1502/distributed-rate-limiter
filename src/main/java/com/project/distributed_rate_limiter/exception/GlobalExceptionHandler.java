package com.project.distributed_rate_limiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(RateLimitExceededException ex) {
        ErrorResponse errorPayload = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(), // 429
                "Too Many Requests",
                ex.getMessage()
        );

       return new ResponseEntity<>(errorPayload, HttpStatus.TOO_MANY_REQUESTS);
    }
}

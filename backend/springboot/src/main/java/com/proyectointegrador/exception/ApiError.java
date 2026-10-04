package com.proyectointegrador.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String path
) {

    public static ApiError of(HttpStatus status, String mensaje, String path) {
        return new ApiError(
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                status.value(),
                status.getReasonPhrase(),
                mensaje,
                path
        );
    }
}

package com.finte.sigapp.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.finte.sigapp.exception.catalog.ErrorCode;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
                log.error("Error de validación: ", ex);
                String mensaje = ex.getBindingResult().getFieldErrors().stream()
                                .map(err -> err.getField() + ":" + err.getDefaultMessage())
                                .collect(Collectors.joining(", "));

                log.warn("Error de validación : {}", mensaje);

                return buildResponse(
                                HttpStatus.BAD_REQUEST,
                                ErrorCode.SIGAPP_400,
                                mensaje);
        }

        @ExceptionHandler(ExpiredJwtException.class)
        public ResponseEntity<ErrorResponse> handleExpiredJwt(ExpiredJwtException e) {
                log.warn("Token expirado : {}", e.getMessage());
                return buildResponse(
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.SIGAPP_402,
                                ErrorCode.SIGAPP_402.getMessage());
        }

        @ExceptionHandler(JwtException.class)
        public ResponseEntity<ErrorResponse> handleJwt(JwtException e) {
                log.warn("Token invalido : {}", e.getMessage());
                return buildResponse(
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.SIGAPP_403,
                                ErrorCode.SIGAPP_403.getMessage());
        }

        // excepciones de negocio
        @ExceptionHandler(BussinessException.class)
        public ResponseEntity<ErrorResponse> handleBussiness(BussinessException e) {
                log.warn("Codigo {} - {}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
                return buildResponse(
                                HttpStatus.BAD_REQUEST,
                                e.getErrorCode(),
                                e.getErrorCode().getMessage());
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
                log.warn("No autorizado: {}", e.getMessage());
                return buildResponse(
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.SIGAPP_401,
                                ErrorCode.SIGAPP_401.getMessage());
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
                log.debug("Recurso estático no encontrado: {}", e.getMessage());
                return buildResponse(
                                HttpStatus.NOT_FOUND,
                                ErrorCode.SIGAPP_404,
                                ErrorCode.SIGAPP_404.getMessage());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
                log.error("Error inesperado: {}", e.getMessage());
                return buildResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                ErrorCode.SIGAPP_500,
                                ErrorCode.SIGAPP_500.getMessage());
        }

        private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, ErrorCode errorCode, String message) {
                return ResponseEntity.status(status)
                                .body(
                                                ErrorResponse.builder()
                                                                .success(false)
                                                                .code(errorCode.getCode())
                                                                .message(message)
                                                                .build());
        }
}

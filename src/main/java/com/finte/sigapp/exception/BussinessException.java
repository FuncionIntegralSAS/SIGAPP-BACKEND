package com.finte.sigapp.exception;

import com.finte.sigapp.exception.catalog.ErrorCode;

import lombok.Getter;

@Getter
public class BussinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String message;

    public BussinessException(ErrorCode errorCode, String message) {
        // super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.message = message;
    }

    public BussinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.message = null;
    }
}
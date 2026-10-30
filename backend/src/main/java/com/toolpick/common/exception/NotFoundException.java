package com.toolpick.common.exception;

/** 없는 자원 요청. GlobalExceptionHandler가 404로 바꾼다 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

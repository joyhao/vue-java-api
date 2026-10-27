package com.joykit.common.exception;

public final class BusinessException extends RuntimeException {
    public enum Kind { VALIDATION, NOT_FOUND, CONFLICT, FORBIDDEN }
    private final Kind kind;

    public BusinessException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}

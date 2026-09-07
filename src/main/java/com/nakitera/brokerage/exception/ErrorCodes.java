package com.nakitera.brokerage.exception;

public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    public static final String INSUFFICIENT_USABLE_BALANCE = "INSUFFICIENT_USABLE_BALANCE";
    public static final String ORDER_NOT_PENDING = "ORDER_NOT_PENDING";
    public static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String IDEMPOTENCY_KEY_CONFLICT = "IDEMPOTENCY_KEY_CONFLICT";

    private ErrorCodes() {
    }
}

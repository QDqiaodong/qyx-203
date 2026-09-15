package com.airport.maternity.exception;

/**
 * 高峰窗内同时在用数超出「分区+设备类型」上限时抛出。
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String message) {
        super(message);
    }
}

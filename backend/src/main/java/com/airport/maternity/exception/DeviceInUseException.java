package com.airport.maternity.exception;

/**
 * 删除设备时仍存在进行中（现场还在用）的占用，删除被拦截时抛出。
 * 消息内需点名是哪几段进行中的占用。
 */
public class DeviceInUseException extends RuntimeException {

    public DeviceInUseException(String message) {
        super(message);
    }
}

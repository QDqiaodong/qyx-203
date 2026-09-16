package com.airport.maternity.exception;

/**
 * 同一把枪同一个当班日已经落过校准结论，后到的登记请求冲突时抛出。
 * 先落地的那份结论为准，后到的不能改先写的结论（只追加、不覆盖）。
 */
public class CalibrationConflictException extends RuntimeException {

    public CalibrationConflictException(String message) {
        super(message);
    }
}

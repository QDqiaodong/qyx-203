package com.airport.maternity.exception;

/**
 * 试图把当班日校准结论为「不通过」（或名单已因校准没过撤下）的枪
 * 再列入当班可用名单时抛出：没过的枪不能当班。
 */
public class CalibrationNotPassedException extends RuntimeException {

    public CalibrationNotPassedException(String message) {
        super(message);
    }
}

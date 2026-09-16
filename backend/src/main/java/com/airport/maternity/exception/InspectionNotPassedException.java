package com.airport.maternity.exception;

/**
 * 当天最近一次开班巡检不是「通过」（未巡检或巡检不通过）时，
 * 仍要给设备新挂「生效中」时段，拦截时抛出。
 */
public class InspectionNotPassedException extends IllegalArgumentException {

    public InspectionNotPassedException(String message) {
        super(message);
    }
}

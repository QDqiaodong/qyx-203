package com.airport.maternity.config;

import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.exception.CalibrationConflictException;
import com.airport.maternity.exception.CalibrationNotPassedException;
import com.airport.maternity.exception.CapacityExceededException;
import com.airport.maternity.exception.DeviceInUseException;
import com.airport.maternity.exception.InspectionNotPassedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseDTO<Void> handleCapacityExceeded(CapacityExceededException e) {
        return ResponseDTO.error(409, e.getMessage());
    }

    @ExceptionHandler(DeviceInUseException.class)
    public ResponseDTO<Void> handleDeviceInUse(DeviceInUseException e) {
        return ResponseDTO.error(409, e.getMessage());
    }

    @ExceptionHandler(InspectionNotPassedException.class)
    public ResponseDTO<Void> handleInspectionNotPassed(InspectionNotPassedException e) {
        return ResponseDTO.error(409, e.getMessage());
    }

    /** 同枪同当班日已有先落库的校准结论：后到的不能改先写的 */
    @ExceptionHandler(CalibrationConflictException.class)
    public ResponseDTO<Void> handleCalibrationConflict(CalibrationConflictException e) {
        return ResponseDTO.error(409, e.getMessage());
    }

    /** 校准不通过的枪仍想列入当班可用名单：拦下 */
    @ExceptionHandler(CalibrationNotPassedException.class)
    public ResponseDTO<Void> handleCalibrationNotPassed(CalibrationNotPassedException e) {
        return ResponseDTO.error(409, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseDTO<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseDTO.error(400, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseDTO<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数校验失败" : error.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseDTO.error(400, message);
    }
}

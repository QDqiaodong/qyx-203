package com.airport.maternity.config;

import com.airport.maternity.entity.PeakWindow;
import com.airport.maternity.repository.PeakWindowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 初始化两段运营高峰窗：早出港高峰、晚进港高峰。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private PeakWindowRepository peakWindowRepository;

    @Override
    public void run(String... args) {
        if (peakWindowRepository.count() == 0) {
            peakWindowRepository.save(new PeakWindow(null, "早出港高峰", LocalTime.of(6, 0), LocalTime.of(9, 0)));
            peakWindowRepository.save(new PeakWindow(null, "晚进港高峰", LocalTime.of(21, 0), LocalTime.of(23, 59)));
        }
    }
}

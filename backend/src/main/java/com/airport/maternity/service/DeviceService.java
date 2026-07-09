package com.airport.maternity.service;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.entity.Device;
import com.airport.maternity.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    public Page<Device> findAll(String terminalArea, String deviceType, Pageable pageable) {
        if (terminalArea != null && !terminalArea.isEmpty() && deviceType != null && !deviceType.isEmpty()) {
            return deviceRepository.findByTerminalAreaAndDeviceType(terminalArea, deviceType, pageable);
        } else if (terminalArea != null && !terminalArea.isEmpty()) {
            return deviceRepository.findByTerminalArea(terminalArea, pageable);
        } else if (deviceType != null && !deviceType.isEmpty()) {
            return deviceRepository.findByDeviceType(deviceType, pageable);
        }
        return deviceRepository.findAll(pageable);
    }

    public Optional<Device> findById(Long id) {
        return deviceRepository.findById(id);
    }

    public Optional<Device> findByDeviceCode(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode);
    }

    @Transactional
    public Device save(DeviceDTO dto) {
        Device device = new Device();
        if (dto.getId() != null) {
            device = deviceRepository.findById(dto.getId()).orElse(new Device());
        }
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceType(dto.getDeviceType());
        device.setTerminalArea(dto.getTerminalArea());
        device.setStatus(dto.getStatus());
        return deviceRepository.save(device);
    }

    @Transactional
    public void deleteById(Long id) {
        deviceRepository.deleteById(id);
    }

    public List<Device> findAllDevices() {
        return deviceRepository.findAll();
    }
}
package com.airport.maternity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;

/**
 * 高峰窗定义（如：早出港高峰、晚进港高峰）。
 * 高峰窗为全局运营时段，按日循环生效。
 */
@Entity
@Table(name = "peak_window")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeakWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}

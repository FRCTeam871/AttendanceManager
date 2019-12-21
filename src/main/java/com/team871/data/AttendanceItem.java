package com.team871.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AttendanceItem {
    private LocalDateTime inTime = null;
    private LocalDateTime outTime = null;

    public AttendanceItem() {
        inTime = LocalDateTime.now();
    }

    public AttendanceItem(LocalDate date) {
        inTime = date.atTime(LocalTime.now());
    }

    public AttendanceItem(LocalDate date, LocalTime intime, LocalTime outTime) {
        this.inTime = intime.atDate(date);
        this.outTime = outTime.atDate(date);
    }

    public LocalDateTime getInTime() {
        return inTime;
    }

    public LocalDateTime getOutTime() {
        return outTime;
    }

    public void signOut() {
        outTime = LocalDateTime.now();
    }
}

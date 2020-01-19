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
        this(date, LocalTime.now(), null);
    }

    public AttendanceItem(LocalDate date, LocalTime inTime, LocalTime outTime) {
        if(inTime != null) {
            this.inTime = inTime.atDate(date);
        }

        if(outTime != null) {
            this.outTime = outTime.atDate(date);
        }
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

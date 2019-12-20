package com.team871.data;

import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttendanceItem {
    private static final Pattern IN_OUT_PATTERN = Pattern.compile("In:\\s+(\\S+)(?:\\s+Out:\\s+(\\S+))?");

    private ZonedDateTime inTime = null;
    private ZonedDateTime outTime = null;

    public AttendanceItem() {
        inTime = ZonedDateTime.now();
    }

    public AttendanceItem(String data) {
        final Matcher matcher = IN_OUT_PATTERN.matcher(data);
        if(matcher.matches()) {
            // TODO: Incomplete
            String inTime = matcher.group(1);
            String outTime = matcher.group(2);
        }
    }

    public ZonedDateTime getInTime() {
        return inTime;
    }

    public ZonedDateTime getOutTime() {
        return outTime;
    }

    public void signOut() {
        outTime = ZonedDateTime.now();
    }
}

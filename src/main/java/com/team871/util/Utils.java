package com.team871.util;

import java.text.DateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class Utils {
    private static final Pattern datePattern = Pattern.compile("\\d{1,2}-[a-zA-Z]{3}");
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-d-MMM");

    public static final String FIRST_NAME_COL = "First";
    public static final String LAST_NAME_COL = "Last";
    public static final String ID_COL = "ID";
    public static final String TOTAL_COL = "Total";
    public static final String SAFETY_COL = "Safety";
    public static final String FIRST_REG_COL = "First Reg.";
    public static final long HOUR_OF_NANOS = 1_000_000_000L * 3600;

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    public static boolean isNullOrEmpty(String val) {
        return val == null || val.isEmpty();
    }

    public static LocalDate getLocalDate(String date) {
        if(datePattern.matcher(date).matches()) {
            date = LocalDate.now().getYear() + "-" + date;
            return LocalDate.parse(date, dateFormat);
        } else {
            final String[] dateParts = date.split("/");
            return LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(dateParts[0]), Integer.parseInt(dateParts[1]));
        }
    }
}

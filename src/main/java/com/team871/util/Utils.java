package com.team871.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Utils {
    public static final String FIRST_NAME_COL = "First";
    public static final String LAST_NAME_COL = "Last";
    public static final String ID_COL = "ID";
    public static final String TOTAL_COL = "Total";
    public static final String SAFETY_COL = "Safety";
    public static final String FIRST_REG_COL = "First Reg.";

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");

    public static boolean isNullOrEmpty(String val) {
        return val == null || val.isEmpty();
    }

    public static LocalDate getLocalDate(String date) {
        final String[] dateParts = date.split("/");
        return LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(dateParts[0]), Integer.parseInt(dateParts[1]));
    }
}

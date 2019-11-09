package com.team871.data;

import com.team871.ui.LoginType;
import com.team871.ui.StudentTable;
import com.team871.util.Settings;
import com.team871.util.ThrowingRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class Student implements Comparable<Student>{
    private final String firstName;
    private final String lastName;

    private int id = -1;
    private int grade = -1;
    private Subteam subteam = null;
    private SafeteyFormState safeteyFormState = null;
    private FirstRegistration registration = null;

    Map<String, AttendanceItem> attendance;

    public Student(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void populateFromRow(int row, @NotNull StudentTable.SheetConfig sheet) {
        checkAndTry(sheet.getValue(row, "SID"), v -> id = Integer.parseInt(v));
        checkAndTry(sheet.getValue(row, "Safety"), v -> safeteyFormState = SafeteyFormState.valueOf(v));
        checkAndTry(sheet.getValue(row, "First Reg."), v -> registration = FirstRegistration.valueOf(v));
        checkAndTry(sheet.getValue(row, "Grade"), v -> grade = Integer.parseInt(v));
        checkAndTry(sheet.getValue(row, "Subteam"), v -> subteam = Subteam.valueOf(v));
    }

    public void processAttendance(int row, @NotNull StudentTable.SheetConfig sheet) {
        final int firstDataColumn = Settings.getInstance().getAttendanceFirstDataColumn();

        // This is actually pretty terrible.
        for(int i = firstDataColumn; i < sheet.getColumnCount(); i++) {
            final String date = sheet.getHeaderValue(i);
            if(Settings.isNullOrEmpty(date)) {
                continue;
            }

            String cellValue = sheet.getValue(row, date);
            if(Settings.isNullOrEmpty(cellValue)) {
                continue;
            }

            if(Settings.getInstance().getLoginType() == LoginType.IN_OUT) {
                attendance.put(date, new AttendanceItem(cellValue));
            } else {
                attendance.put(date, new AttendanceItem());
            }
        }
    }

    private <E extends Exception> void checkAndTry(String value, ThrowingRunnable<String, E> action) {
        if(!Settings.isNullOrEmpty(value)) {
            try {
                action.run(value);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int compareTo(@NotNull Student o) {
        int result = lastName.compareTo(o.lastName);
        if(result == 0) {
            result = firstName.compareTo(o.firstName);
        }

        return result;
    }

    public int getId() {
        return id;
    }
}

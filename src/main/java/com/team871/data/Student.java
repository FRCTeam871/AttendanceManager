package com.team871.data;

import com.team871.ui.LoginType;
import com.team871.ui.StudentTable;
import com.team871.util.Settings;
import com.team871.util.ThrowingRunnable;
import org.apache.poi.ss.usermodel.Row;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Map;

public class Student implements Comparable<Student>{
    private final String firstName;
    private final String lastName;
    private final Map<String, AttendanceItem> attendance = new HashMap<>();

    private String id = null;
    private int grade = -1;
    private Subteam subteam = null;
    private SafeteyFormState safeteyFormState = null;
    private FirstRegistration registration = null;

    private Row rosterRow;
    private Row attendanceRow;

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
        rosterRow = sheet.getRow(row);
        id = sheet.getValue(row, "SID");

        Integer val = sheet.getIntValue(row, "Grade");
        grade = val == null ? -1 : val;

        checkAndTry(sheet.getValue(row, "Safety"), v -> safeteyFormState = SafeteyFormState.valueOf(v));
        checkAndTry(sheet.getValue(row, "First Reg."), v -> registration = FirstRegistration.getByKey(v));
        checkAndTry(sheet.getValue(row, "Team"), v -> subteam = Subteam.valueOf(v));
    }

    public void processAttendance(int row, @NotNull StudentTable.SheetConfig sheet) {
        attendanceRow = sheet.getRow(row);
        final int firstDataColumn = Settings.getInstance().getAttendanceFirstDataColumn();

        // This is actually pretty terrible.
        for(int i = firstDataColumn; i < sheet.getColumnCount(); i++) {
            final String date = sheet.getHeaderValue(i);
            if(Settings.isNullOrEmpty(date)) {
                continue;
            }
            if("Pre".equals(date)) {
                break;
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

    public String getId() {
        return id;
    }

    public Row getAttendanceRow() {
        return attendanceRow;
    }

    public boolean isSignedIn(String date) {
        return attendance.get(date) != null;
    }

    public boolean isSignedOut(String date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return false;
        }

        return item.getOutTime() != null;
    }

    public void signIn(String date) {
        attendance.computeIfAbsent(date, AttendanceItem::new);
    }

    public void signOut(String date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return;
        }

        item.signOut();
    }

    public void setId(String sid) {
        if(id != null) {
            throw new IllegalStateException("ID is already set for " + firstName + " " + lastName);
        }
        this.id = sid;
    }

    public ZonedDateTime getSignInTime(String date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return null;
        }

        return item.getInTime();
    }
}

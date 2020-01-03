package com.team871.data;

import com.team871.ui.StudentTable;
import com.team871.util.Settings;
import com.team871.util.ThrowingRunnable;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Student implements Comparable<Student> {
    private String firstName;
    private String lastName;
    private final Map<LocalDate, AttendanceItem> attendance = new HashMap<>();
    private final Map<LocalDate, Cell> dateCellMap = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    private String id = null;
    private int grade = -1;
    private Subteam subteam = null;
    private SafeteyFormState safeteyFormState = null;
    private FirstRegistration registration = null;

    private Row rosterRow;
    private Row attendanceRow;

    public interface Listener {
        void onLogin(Student student);
        void onLogout(Student student);
        void onNameChanged(Student student, String oldLastName, String oldFirstName);
        void onIdChanged();
    }

    public Student(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public SafeteyFormState getSafeteyFormState() {
        return safeteyFormState;
    }

    public FirstRegistration getRegistration() {
        return registration;
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

/*
        Integer val = sheet.getIntValue(row, "Grade");
        grade = val == null ? -1 : val;
*/

        checkAndTry(sheet.getValue(row, "Safety"), v -> safeteyFormState = SafeteyFormState.valueOf(v));
        checkAndTry(sheet.getValue(row, "First Reg."), v -> registration = FirstRegistration.getByKey(v));
        checkAndTry(sheet.getValue(row, "Team"), v -> subteam = Subteam.valueOf(v));
    }

    public void processAttendance(int row, @NotNull StudentTable.SheetConfig sheet) {
        attendanceRow = sheet.getRow(row);
        final int firstDataColumn = Settings.getInstance().getAttendanceFirstDataColumn();

        // This is actually pretty terrible.
        for(int i = firstDataColumn; i < sheet.getColumnCount(); i++) {
            final String dateString = sheet.getHeaderValue(i);
            if(Settings.isNullOrEmpty(dateString)) {
                continue;
            }
            if("Pre".equals(dateString)) {
                break;
            }

            final String[] dateParts = dateString.split("/");
            if(dateParts.length < 2 ) {
                continue;
            }

            final LocalDate date = LocalDate.of(LocalDate.now().getYear(),
                    Integer.parseInt(dateParts[0]),
                    Integer.parseInt(dateParts[1]));

            dateCellMap.put(date, sheet.getCell(row, dateString));
            String cellValue = sheet.getValue(row, dateString);
            if(Settings.isNullOrEmpty(cellValue)) {
                continue;
            }

            attendance.put(date, new AttendanceItem(date));
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

    public boolean isSignedIn(LocalDate date) {
        return attendance.get(date) != null;
    }

    public boolean isSignedOut(LocalDate date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return false;
        }

        return item.getOutTime() != null;
    }

    public void signIn(LocalDate date) {
        final AttendanceItem item = attendance.computeIfAbsent(date, d -> new AttendanceItem(date));

        if(attendanceRow != null) {
            getCellForDate(date).setCellValue("1");
        }

        listeners.forEach(l -> l.onLogin(this));
    }

    public void signOut(LocalDate date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return;
        }

        item.signOut();
        if(attendanceRow != null) {
            getCellForDate(date).setCellValue("2");
        }

        listeners.forEach(l -> l.onLogout(this));
    }

    public void setId(String sid) {
        if(id != null) {
            throw new IllegalStateException("ID is already set for " + firstName + " " + lastName);
        }
        this.id = sid;
    }

    public void setName(String first, String last) {
        final String oldLast = lastName;
        final String oldFirst = firstName;

        this.firstName = first;
        this.lastName = last;
        listeners.forEach(l -> l.onNameChanged(this, oldLast, oldFirst));
    }

    public LocalTime getSignInTime(LocalDate date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return null;
        }

        return item.getInTime().toLocalTime();
    }

    public LocalTime getSignOutTime(LocalDate date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return null;
        }

        return item.getOutTime().toLocalTime();
    }

    private Cell getCellForDate(LocalDate date) {
        return dateCellMap.get(date);
    }
}

package com.team871.data;

import com.team871.util.Settings;
import com.team871.util.ThrowingRunnable;
import com.team871.util.Utils;
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
    private final List<Listener> listeners = new ArrayList<>();

    private String id = null;
    private int grade = -1;
    private Subteam subteam = null;
    private SafeteyFormState safeteyFormState = SafeteyFormState.None;
    private FirstRegistration registration = FirstRegistration.None;

    private int rosterRow;
    private int attendanceRow;

    private final SheetConfig rosterSheet;
    private final SheetConfig attendanceSheet;

    public interface Listener {
        void onLogin(Student student);
        void onLogout(Student student);
        void onNameChanged(Student student, String oldLastName, String oldFirstName);
        void onIdChanged();
    }

    public Student(int row, SheetConfig roster, SheetConfig attendanceSheet) {
        this.rosterRow = row;
        this.lastName = roster.getValue(row, Utils.LAST_NAME_COL);
        this.firstName = roster.getValue(row, Utils.FIRST_NAME_COL);

        this.id = roster.getValue(row, "SID");

        Integer val = roster.getIntValue(row, "Grade");
        this.grade = val == null ? -1 : val;

        checkAndTry(roster.getValue(row, "Safety"), v -> safeteyFormState = SafeteyFormState.valueOf(v));
        checkAndTry(roster.getValue(row, "First Reg."), v -> registration = FirstRegistration.getByKey(v));
        checkAndTry(roster.getValue(row, "Team"), v -> subteam = Subteam.valueOf(v));
        rosterSheet = roster;
        this.attendanceSheet = attendanceSheet;
    }

    public Student(String firstName, String lastName, SheetConfig rosterSheet, SheetConfig attendanceSheet) {
        this.firstName = firstName;
        this.lastName = lastName;

        // Do something smart
        this.rosterSheet = rosterSheet;
        this.attendanceSheet = attendanceSheet;

        rosterRow = rosterSheet.addRow();
        attendanceRow = attendanceSheet.addRow();

        rosterSheet.setCell(rosterRow, Utils.LAST_NAME_COL, true, lastName);
        rosterSheet.setCell(rosterRow, Utils.FIRST_NAME_COL, true, firstName);
        rosterSheet.setCell(rosterRow, Utils.SAFETY_COL, true, SafeteyFormState.None.name());
        rosterSheet.setCell(rosterRow, Utils.FIRST_REG_COL, true, FirstRegistration.None.getKey());

        attendanceSheet.setCell(rosterRow, Utils.LAST_NAME_COL, true, lastName);
        attendanceSheet.setCell(rosterRow, Utils.FIRST_NAME_COL, true, firstName);
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

    public void processAttendance(int row) {
        attendanceRow = row;
        final int firstDataColumn = Settings.getInstance().getAttendanceFirstDataColumn();

        // This is actually pretty terrible.
        for(int i = firstDataColumn; i < attendanceSheet.getColumnCount(); i++) {
            final String dateString = attendanceSheet.getHeaderValue(i);
            if(Settings.isNullOrEmpty(dateString)) {
                continue;
            }
            if(Utils.TOTAL_COL.equals(dateString)) {
                break;
            }

            final String[] dateParts = dateString.split("/");
            if(dateParts.length < 2 ) {
                continue;
            }
            final LocalDate date = LocalDate.of(LocalDate.now().getYear(),
                    Integer.parseInt(dateParts[0]),
                    Integer.parseInt(dateParts[1]));

            String cellValue = attendanceSheet.getValue(row, dateString);
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

        updateAttendanceCell(date, item);
        listeners.forEach(l -> l.onLogin(this));
    }

    public void signOut(LocalDate date) {
        final AttendanceItem item = attendance.get(date);
        if(item == null) {
            return;
        }

        item.signOut();
        updateAttendanceCell(date, item);
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

        rosterSheet.setCell(rosterRow, Utils.LAST_NAME_COL, true, last);
        rosterSheet.setCell(rosterRow, Utils.FIRST_NAME_COL, true, first);
        attendanceSheet.setCell(rosterRow, Utils.LAST_NAME_COL, true, last);
        attendanceSheet.setCell(rosterRow, Utils.FIRST_NAME_COL, true, first);
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

    private void updateAttendanceCell(LocalDate date, AttendanceItem item) {
        attendanceSheet.setCell(attendanceRow, Utils.DATE_FORMATTER.format(date), true, "(" +
                Utils.TIME_FORMATTER.format(item.getInTime()) + "," +
                (item.getOutTime() == null ? "" : Utils.TIME_FORMATTER.format(item.getOutTime())) + ")");
    }
}

package com.team871.data;

import com.team871.util.Settings;
import com.team871.util.ThrowingRunnable;
import com.team871.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Member implements Comparable<Member> {
    private static final Logger log = LoggerFactory.getLogger("Member");
    private static final DecimalFormat DURATION_FORMAT = new DecimalFormat("##.##");

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

    private double totalHours = 0;

    public interface Listener {
        void onLogin(Member member);
        void onLogout(Member member);
        void onNameChanged(Member member, String oldLastName, String oldFirstName);
        void onIdChanged(Member member, String oldSid);
    }

    public Member(int row, SheetConfig roster, SheetConfig attendanceSheet) {
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

    public Member(String firstName, String lastName, SheetConfig rosterSheet, SheetConfig attendanceSheet) {
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

            final boolean shouldReformat = cellValue.matches(".*[()]+.*");

            cellValue = cellValue.replaceAll("[()]", "");
            final String[] timeParts = cellValue.split(",");
            LocalTime inTime = LocalTime.now();
            LocalTime outTime = null;

            if(timeParts.length >= 1 && !Utils.isNullOrEmpty(timeParts[0])) {
                try {
                    inTime = LocalTime.parse(timeParts[0]);
                } catch(DateTimeParseException ex) {
                    log.error("Failed to parse in time " + timeParts[0]);
                }
            }

            if(timeParts.length >= 2 && !Utils.isNullOrEmpty(timeParts[1])) {
                try {
                    outTime = LocalTime.parse(timeParts[1]);
                } catch(DateTimeParseException ex) {
                    log.error("Failed to parse out time " + timeParts[1]);
                }
            }

            final AttendanceItem item = new AttendanceItem(date, inTime, outTime);
            if(timeParts.length == 2 && shouldReformat) {
                updateAttendanceCell(date, item);
            }

            attendance.put(date, item);
        }

        maybeUpdateHours();
    }

    private <E extends Exception> void checkAndTry(String value, ThrowingRunnable<String, E> action) {
        if(!Settings.isNullOrEmpty(value)) {
            try {
                action.run(value);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int compareTo(@NotNull Member o) {
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
        final String oldId = id;
        this.id = sid;
        rosterSheet.setCell(rosterRow, "SID", true, sid);

        listeners.forEach(l -> l.onIdChanged(this, oldId));
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
    public double getTotalHours() {
        return totalHours;
    }

    private void updateAttendanceCell(LocalDate date, AttendanceItem item) {
        final String columnName = Utils.DATE_FORMATTER.format(date);
        if(!attendanceSheet.columnExists(columnName)) {
            attendanceSheet.addColumn(columnName);
        }

        String sheetLine = Utils.TIME_FORMATTER.format(item.getInTime());
        if(item.getOutTime() != null) {
            sheetLine += "," + Utils.TIME_FORMATTER.format(item.getOutTime())
                       + "," + DURATION_FORMAT.format(Duration.between(item.getInTime(), item.getOutTime()).toNanos() / (double)Utils.HOUR_OF_NANOS) ;
        }

        attendanceSheet.setCell(attendanceRow, columnName, true, sheetLine);
        maybeUpdateHours();
    }

    private void maybeUpdateHours() {
        long totalTime = 0;

        for(AttendanceItem item : attendance.values()) {
            if(item.getInTime() != null && item.getOutTime() != null) {
                totalTime += Duration.between(item.getInTime(), item.getOutTime()).toNanos();
            }
        }

        this.totalHours = totalTime/(double)Utils.HOUR_OF_NANOS;
        rosterSheet.setCell(rosterRow, "Hours", false, DURATION_FORMAT.format(totalHours));
    }
}

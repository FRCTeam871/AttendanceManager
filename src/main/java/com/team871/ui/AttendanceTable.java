package com.team871.ui;

import com.team871.data.SheetConfig;
import com.team871.data.Member;
import com.team871.exception.RobotechException;
import com.team871.util.Settings;
import com.team871.util.Utils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.*;

public class AttendanceTable {
    private static final Logger logger = LoggerFactory.getLogger(AttendanceTable.class);

    private final Map<String, Map<String, Member>> studentsByName = new TreeMap<>();
    private final Map<String, Member> studentsById = new HashMap<>();
    private final List<Member> allMembers = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Member.Listener studentListener = new Member.Listener() {
        @Override
        public void onLogin(Member member) {
            listeners.forEach(l -> l.onSignIn(member));
        }

        @Override
        public void onLogout(Member member) {
            listeners.forEach(l -> l.onSignOut(member));
        }

        @Override
        public void onNameChanged(Member member, String oldLastName, String oldFirstName) {
            Map<String, Member> byFirstName = studentsByName.get(oldLastName);
            if(byFirstName == null) {
                throw new IllegalStateException("Student never existed");
            }

            byFirstName.remove(oldFirstName);
            byFirstName = studentsByName.computeIfAbsent(member.getLastName(), n -> new HashMap<>());
            byFirstName.put(member.getFirstName(), member);

            sortStudents();
            listeners.forEach(l -> l.nameChanged(member));
        }

        @Override
        public void onIdChanged() {

        }
    };

    private void sortStudents() {
        allMembers.sort(Comparator.comparing(Member::getLastName).thenComparing(Member::getFirstName));
    }

    private Workbook workbook;
    private SheetConfig roster;
    private SheetConfig attendance;
    private final String rosterSheetName;
    private final String attendanceSheetName;
    private final int attHeaderRow;
    private final int rosterHeaderRow;
    private final int attFirstRow;
    private final int rosterFirstRow;

    private final List<LocalDate> attendanceDates = new ArrayList<>();
    private boolean unsaved = false;

    public interface Listener {
        void onSignIn(Member member);
        void onSignOut(Member member);
        void nameChanged(Member member);
        void onStudentAdded(Member member);
    }

    public AttendanceTable(Workbook workbook, String rosterSheet, String attendanceSheet,
                           int attHeaderRow, int rosterHeaderRow,
                           int attFirstRow, int rosterFirstRow) throws RobotechException {
        this.workbook = workbook;
        this.rosterSheetName = rosterSheet;
        this.attendanceSheetName = attendanceSheet;
        this.attHeaderRow = attHeaderRow;
        this.rosterHeaderRow = rosterHeaderRow;
        this.attFirstRow = attFirstRow;
        this.rosterFirstRow = rosterFirstRow;

        loadAttendance();
        Settings.getInstance().addListener(this::updateDate);
    }

    public Map<String, Member> getStudentsWithLastName(String lastName) {
        return studentsByName.getOrDefault(lastName, Collections.emptyMap());
    }

    public int getStudentIndex(Member highlightMember) {
        return allMembers.indexOf(highlightMember);
    }

    public void createStudent(String first, String last) {
        final Map<String, Member> byFirstName = studentsByName.computeIfAbsent(last, n -> new HashMap<>());
        if(byFirstName.containsKey(first)) {
            throw new IllegalStateException("Name already exists!");
        }

        final Member member = new Member(first, last, roster, attendance);
        member.addListener(studentListener);

        byFirstName.put(member.getFirstName(), member);
        allMembers.add(member);
        sortStudents();

        listeners.forEach(l -> l.onStudentAdded(member));
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void loadAttendance() throws RobotechException {
        logger.info("Loading attendance sheet");
        final Sheet attendanceSheet = workbook.getSheet(attendanceSheetName);
        if(attendanceSheet == null) {
            throw new RuntimeException("Attendance worksheet " + attendanceSheetName + " does not exist");
        }
        attendance = new SheetConfig(attendanceSheet, attHeaderRow);

        final Sheet rosterSheet = workbook.getSheet(rosterSheetName);
        if(rosterSheet == null) {
            throw new RuntimeException("Roster worksheet " + rosterSheetName + " does not exist");
        }
        roster = new SheetConfig(rosterSheet,rosterHeaderRow);

        // Now process the sheets into a set of students and their attendances.
        // Start with the roster
        for(int i = 0; i < roster.getDataRowCount(); i++) {
            if(!roster.rowExists(i)) {
                continue;
            }

            final String lastName = roster.getValue(i, Utils.LAST_NAME_COL);
            final String firstName = roster.getValue(i, Utils.FIRST_NAME_COL);

            final Map<String, Member> byFirstName = studentsByName.computeIfAbsent(lastName, k -> new TreeMap<>());
            if(byFirstName.containsKey(firstName)) {
                logger.error("Duplicate name! " + firstName + " " + lastName);
                continue;
            }

            final Member member = new Member(i, roster, attendance);
            byFirstName.put(firstName, member);
            allMembers.add(member);

            // If an ID existed, add to the mapping
            if(member.getId() != null) {
                studentsById.put(member.getId(), member);
            }

            member.addListener(studentListener);
        }
        sortStudents();

        // Then process the attendance
        for(int i = attFirstRow; i < attendance.getColumnCount(); i++) {
            final String headerVal = attendance.getHeaderValue(i);
            if("Total".equals(headerVal)) {
                break;
            }
            switch(headerVal) {
                case Utils.ID_COL:
                case Utils.FIRST_NAME_COL:
                case Utils.LAST_NAME_COL:
                    break;
                default:
                    attendanceDates.add(Utils.getLocalDate(headerVal));
            }
        }
        attendanceDates.sort(Comparator.comparing(LocalDate::toEpochDay));

        for(int i = 0; i<attendance.getDataRowCount(); i++) {
            if(!attendance.rowExists(i)) {
                continue;
            }

            final String lastName = attendance.getValue(i, Utils.LAST_NAME_COL);
            if("#".equals(lastName)) {
                break;
            }

            final String firstName = attendance.getValue(i, Utils.FIRST_NAME_COL);

            final Map<String, Member> byFirstName = studentsByName.get(lastName);
            if(byFirstName == null || byFirstName.isEmpty()) {
                logger.warn("No student `" + firstName + " " + lastName + "` exists.");
                continue;
            }

            final Member member = byFirstName.get(firstName);
            if(member == null) {
                logger.warn("No student `" + firstName + " " + lastName + "` exists.");
                continue;
            }

            member.processAttendance(i);
        }

        updateDate();
    }

    public Member getStudent(int index) {
        return allMembers.get(index);
    }

    public int getStudentCount() {
        return allMembers.size();
    }

    public Member getStudentById(String id) {
        return studentsById.get(id);
    }

    public Map<String, Member> getStudentsByLastName(String lastName) {
        final Map<String, Member> byFirstName = studentsByName.get(lastName);
        if(byFirstName == null || byFirstName.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(byFirstName);
    }

    // TODO:  This doesn't work anymore.
    public boolean hasUnsaved() {
        return unsaved;
    }

    public void setSaved() {
        unsaved = false;
    }

    List<LocalDate> getAttendanceDates() {
        return attendanceDates;
    }

    public boolean areAllSignedOut() {
        if(Settings.getInstance().getLoginType() == LoginType.IN_ONLY) {
            return false;
        }

        return allMembers.stream()
                .filter(s -> s.isSignedIn(Settings.getInstance().getDate()))
                .allMatch(s -> s.isSignedOut(Settings.getInstance().getDate()));
    }

    public void forceSignOut() {
        final LocalDate date = Settings.getInstance().getDate();
        allMembers.stream()
                .filter(s -> s.isSignedIn(date))
                .forEach(s -> s.signOut(date));
    }

    private void updateDate() {
        final LocalDate date = Settings.getInstance().getDate();
        if(attendanceDates.stream().noneMatch(d -> d.isEqual(date))) {
            attendanceDates.add(date);
            attendanceDates.sort(Comparator.comparing(LocalDate::toEpochDay));
        }
    }
}

package com.team871.ui;

import com.team871.data.SheetConfig;
import com.team871.data.Student;
import com.team871.exception.RobotechException;
import com.team871.util.Settings;
import com.team871.util.Utils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.*;

public class StudentTable {
    private static final Logger logger = LoggerFactory.getLogger(StudentTable.class);

    private final Map<String, Map<String, Student>> studentsByName = new TreeMap<>();
    private final Map<String, Student> studentsById = new HashMap<>();
    private final List<Student> allStudents = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Student.Listener studentListener = new Student.Listener() {
        @Override
        public void onLogin(Student student) {
            listeners.forEach(l -> l.onSignIn(student));
        }

        @Override
        public void onLogout(Student student) {
            listeners.forEach(l -> l.onSignOut(student));
        }

        @Override
        public void onNameChanged(Student student, String oldLastName, String oldFirstName) {
            Map<String, Student> byFirstName = studentsByName.get(oldLastName);
            if(byFirstName == null) {
                throw new IllegalStateException("Student never existed");
            }

            byFirstName.remove(oldFirstName);
            byFirstName = studentsByName.computeIfAbsent(student.getLastName(), n -> new HashMap<>());
            byFirstName.put(student.getFirstName(), student);

            sortStudents();
            listeners.forEach(l -> l.nameChanged(student));
        }

        @Override
        public void onIdChanged() {

        }
    };

    private void sortStudents() {
        allStudents.sort(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName));
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
        void onSignIn(Student student);
        void onSignOut(Student student);
        void nameChanged(Student student);
        void onStudentAdded(Student student);
    }

    public StudentTable(Workbook workbook, String rosterSheet, String attendanceSheet,
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

    public Map<String, Student> getStudentsWithLastName(String lastName) {
        return studentsByName.getOrDefault(lastName, Collections.emptyMap());
    }

    public int getStudentIndex(Student highlightStudent) {
        return allStudents.indexOf(highlightStudent);
    }

    public void createStudent(String first, String last) {
        final Map<String, Student> byFirstName = studentsByName.computeIfAbsent(last, n -> new HashMap<>());
        if(byFirstName.containsKey(first)) {
            throw new IllegalStateException("Name already exists!");
        }

        final Student student = new Student(first, last, roster, attendance);
        student.addListener(studentListener);

        byFirstName.put(student.getFirstName(), student);
        allStudents.add(student);
        sortStudents();

        listeners.forEach(l -> l.onStudentAdded(student));
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

            final Map<String, Student> byFirstName = studentsByName.computeIfAbsent(lastName, k -> new TreeMap<>());
            if(byFirstName.containsKey(firstName)) {
                logger.error("Duplicate name! " + firstName + " " + lastName);
                continue;
            }

            final Student student = new Student(i, roster, attendance);
            byFirstName.put(firstName, student);
            allStudents.add(student);

            // If an ID existed, add to the mapping
            if(student.getId() != null) {
                studentsById.put(student.getId(), student);
            }

            student.addListener(studentListener);
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

            final Map<String, Student> byFirstName = studentsByName.get(lastName);
            if(byFirstName == null || byFirstName.isEmpty()) {
                logger.warn("No student `" + firstName + " " + lastName + "` exists.");
                continue;
            }

            final Student student = byFirstName.get(firstName);
            if(student == null) {
                logger.warn("No student `" + firstName + " " + lastName + "` exists.");
                continue;
            }

            student.processAttendance(i);
        }

        updateDate();
    }

    public Student getStudent(int index) {
        return allStudents.get(index);
    }

    public int getStudentCount() {
        return allStudents.size();
    }

    public Student getStudentById(String id) {
        return studentsById.get(id);
    }

    public Map<String, Student> getStudentsByLastName(String lastName) {
        final Map<String, Student> byFirstName = studentsByName.get(lastName);
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

        return allStudents.stream()
                .filter(s -> s.isSignedIn(Settings.getInstance().getDate()))
                .allMatch(s -> s.isSignedOut(Settings.getInstance().getDate()));
    }

    public void forceSignOut() {
        final LocalDate date = Settings.getInstance().getDate();
        allStudents.stream()
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

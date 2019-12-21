package com.team871.ui;

import com.team871.data.Student;
import com.team871.exception.RobotechException;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StudentTable {
    private static final Logger logger = LoggerFactory.getLogger(StudentTable.class);
    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");

    private final Map<String, Map<String, Student>> studentsByName = new TreeMap<>();
    private final Map<String, Student> studentsById = new HashMap<>();
    private final Path worksheetPath;
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

            allStudents.sort(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName));
            listeners.forEach(l -> l.nameChanged(student));
        }

        @Override
        public void onIdChanged() {

        }
    };

    private Workbook workbook;
    private SheetConfig roster;
    private SheetConfig attendance;

    private final List<LocalDate> attendanceDates = new ArrayList<>();
    private int currentDateColumn = -1;
    private boolean unsaved = false;

    public Map<String, Student> getStudentsWithLastName(String lastName) {
        return studentsByName.getOrDefault(lastName, Collections.emptyMap());
    }

    public static class SheetConfig {
        private final Sheet sheet;
        private final int headerRow;
        private final int columnCount;
        private final Map<String, Integer> columnMap;

        SheetConfig(Sheet sheet, int headerRow) {
            this.sheet = sheet;
            this.headerRow = headerRow;
            columnMap = new LinkedHashMap<>();

            final Row header = sheet.getRow(headerRow);
            columnCount = header.getLastCellNum();
            for(int i = 0; i < header.getLastCellNum(); i++) {
                final Cell cell = header.getCell(i);
                if(cell == null || cell.getCellType() == CellType.FORMULA) {
                    continue;
                }

                columnMap.put(FORMATTER.formatCellValue(cell), i);
            }
        }

        public String getValue(int row, String column) {
            final Integer cellIndex = columnMap.get(column);
            if(cellIndex == null) {
                return null;
            }

            return FORMATTER.formatCellValue(sheet.getRow(row + headerRow + 1).getCell(cellIndex));
        }

        public Integer getIntValue(int row, String column) {
            final Integer cellIndex = columnMap.get(column);
            if(cellIndex == null) {
                return null;
            }

            return (int)sheet.getRow(row + headerRow + 1).getCell(cellIndex).getNumericCellValue();
        }

        public String getHeaderValue(int cell) {
            return sheet.getRow(headerRow).getCell(cell).getStringCellValue();
        }

        public int getDataRowCount() {
            return sheet.getLastRowNum() - headerRow;
        }

        public int getColumnCount() {
            return columnCount;
        }

        public boolean rowExists(int row) {
            return sheet.getRow(row + headerRow + 1) != null;
        }

        public Row getRow(int row) {
            return sheet.getRow(row + headerRow + 1);
        }
    }

    public interface Listener {
        void onSignIn(Student student);
        void onSignOut(Student student);
        void nameChanged(Student student);
    }

    public StudentTable(Path worksheetPath) throws RobotechException {
        this.worksheetPath = worksheetPath;
        loadAttendance();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void loadAttendance() throws RobotechException {
        logger.info("Loading attendance sheet from " + worksheetPath);
        try(final FileInputStream stream = new FileInputStream(worksheetPath.toFile())) {
            workbook = WorkbookFactory.create(stream);

            final Sheet attendanceSheet = workbook.getSheet(Settings.getInstance().getAttendanceSheet());
            if(attendanceSheet == null) {
                throw new RuntimeException("Attendance worksheet " + Settings.getInstance().getAttendanceSheet() + " does not exist");
            }
            attendance = new SheetConfig(attendanceSheet, Settings.getInstance().getAttendanceHeaderRow());

            final Sheet rosterSheet = workbook.getSheet(Settings.getInstance().getRosterSheet());
            if(rosterSheet == null) {
                throw new RuntimeException("Roster worksheet " + Settings.getInstance().getRosterSheet() + " does not exist");
            }
            roster = new SheetConfig(rosterSheet, Settings.getInstance().getRosterHeaderRow());

            // Now process the sheets into a set of students and their attendances.
            // Start with the roster
            for(int i = 0; i < roster.getDataRowCount(); i++) {
                if(!roster.rowExists(i)) {
                    continue;
                }

                final String lastName = roster.getValue(i, "Last");
                final String firstName = roster.getValue(i, "First");

                final Map<String, Student> byFirstName = studentsByName.computeIfAbsent(lastName, k -> new TreeMap<>());
                if(byFirstName.containsKey(firstName)) {
                    logger.error("Duplicate name! " + firstName + " " + lastName);
                    continue;
                }

                final Student student = new Student(firstName, lastName);
                student.populateFromRow(i, roster);
                byFirstName.put(firstName, student);
                allStudents.add(student);

                // If an ID existed, add to the mapping
                if(student.getId() != null) {
                    studentsById.put(student.getId(), student);
                }

                student.addListener(studentListener);
            }
            allStudents.sort(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName));

            setDate(Settings.getInstance().getDate());

            // Then process the attendance
            for(int i = Settings.getInstance().getAttendanceFirstDataColumn(); i < attendance.getColumnCount(); i++) {
                final String headerVal = attendance.getHeaderValue(i);
                if("Pre".equals(headerVal)) {
                    break;
                }
                switch(headerVal) {
                    case "ID":
                    case "First":
                    case "Last":
                        break;
                    default:
                        attendanceDates.add(BarcodeUtils.getLocalDate(headerVal));
                }
            }

            for(int i = 0; i<attendance.getDataRowCount(); i++) {
                if(!attendance.rowExists(i)) {
                    continue;
                }

                final String lastName = attendance.getValue(i, "Last");
                if("#".equals(lastName)) {
                    break;
                }

                final String firstName = attendance.getValue(i, "First");

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

                student.processAttendance(i, attendance);
            }
        } catch (IOException e) {
            throw new RobotechException("Failed to load attendance file", e);
        }
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

    public boolean save(File saveTo) {
        System.out.println("Saving attendance to " + saveTo.getAbsolutePath());
        try {
            saveTo.createNewFile(); //create the file if it doesn't exist
            FileOutputStream out = new FileOutputStream(saveTo);
            workbook.write(out); // write the workbook to the file
            out.close();
        } catch(Exception e) {
            logger.warn("Error writing spreadsheet: ", e);
            return false;
        }

        unsaved = false;
        return true;
    }

    public File getFile() {
        return this.worksheetPath.toFile();
    }

    List<LocalDate> getAttendanceDates() {
        return attendanceDates;
    }

    public boolean setDate(LocalDate date) {
        final int maybeDateColumn = getColumnIndexByName(DATE_FORMATTER.format(date));
        if(maybeDateColumn > 0) {
            currentDateColumn = maybeDateColumn;
            return true;
        }

        return false;
    }

    private int getColumnIndexByName(String value) {
        for(int i = Settings.getInstance().getAttendanceFirstDataColumn(); i < roster.getColumnCount(); i++) {
            final String currentValue = attendance.getHeaderValue(i);
            if(Objects.equals(currentValue, value)) {
                return i;
            }
        }

        return -1;
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
}

package com.team871.ui;

import com.team871.data.Student;
import com.team871.exception.RobotechException;
import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class StudentTable {
    private static final Logger logger = LoggerFactory.getLogger(StudentTable.class);
    private static final DataFormatter FORMATTER = new DataFormatter();

    private final Map<String, Map<String, Student>> studentsByName = new TreeMap<>();
    private final Map<String, Student> studentsById = new HashMap<>();
    private final Path worksheetPath;
    private final List<Student> allStudents = new ArrayList<>();

    private Workbook workbook;
    private SheetConfig roster;
    private SheetConfig attendance;

    private final List<String> attendanceDates = new ArrayList<>();
    private int currentDateColumn = -1;

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
            columnMap = new HashMap<>();

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

    public StudentTable(Path worksheetPath) throws RobotechException {
        this.worksheetPath = worksheetPath;
        loadAttendance();
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
            }

            // Build a list of dates
            for(int i = Settings.getInstance().getAttendanceFirstDataColumn(); i < roster.getColumnCount(); i++) {
                final String value = attendance.getHeaderValue(i);
                if(Settings.getInstance().getDate().equals(value)) {
                    currentDateColumn = i;
                }
            }

            // Then process the attendance
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

    public int getStudentCount() {
        return allStudents.size();
    }

    public int getLastColumn() {
        return roster.getColumnCount();
    }

    public String getValueAt(int row, int col) {
        return FORMATTER.formatCellValue(attendance.getRow(row).getCell(col));
    }

    public int getCurrentDateColumn() {
        return currentDateColumn;
    }

    public Student getStudentById(String id) {
        return studentsById.get(id);
    }
}

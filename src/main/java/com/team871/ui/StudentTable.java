package com.team871.ui;

import com.team871.data.Student;
import com.team871.exception.RobotechException;
import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentTable {
    private static final Logger logger = LoggerFactory.getLogger(StudentTable.class);

    private final Map<String, Map<String, Student>> students = new HashMap<>();
    private final Path worksheetPath;

    private Workbook workbook;
    private SheetConfig roster;
    private SheetConfig attendance;

    private static class SheetConfig {
        private final Sheet sheet;
        private final int headerRow;
        private final Map<String, Integer> columnMap;

        SheetConfig(Sheet sheet, int headerRow) {
            this.sheet = sheet;
            this.headerRow = headerRow;
            columnMap = new HashMap<>();

            final Row header = sheet.getRow(headerRow);
            for(int i = 0; i < header.getLastCellNum(); i++) {
                final Cell cell = header.getCell(i);
                if(cell.getCellType() == CellType.FORMULA) {
                    continue;
                }

                columnMap.put(cell.getStringCellValue(), i);
            }
        }

        public String getValue(int row, String column) {
            final Integer cellIndex = columnMap.get(column);
            if(cellIndex == null) {
                return null;
            }

            return sheet.getRow(row + headerRow).getCell(cellIndex).getStringCellValue();
        }

        public int getDataRowCount() {
            return sheet.getLastRowNum() - headerRow;
        }
    }

    public StudentTable(Path worksheetPath) {
        this.worksheetPath = worksheetPath;
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
                final String lastName = roster.getValue(i, "Last");
                final String firstName = roster.getValue(i, "First");

                final Map<String, Student> byFirstName = students.computeIfAbsent(lastName, k -> new HashMap<>());
                if(byFirstName.containsKey(firstName)) {
                    logger.error("Duplicate name! " + firstName + " " + lastName);
                    continue;
                }

                final Student student = new Student(firstName, lastName);
            }
        } catch (IOException e) {
            throw new RobotechException("Failed to load attendance file", e);
        }
    }
}

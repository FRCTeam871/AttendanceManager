package com.team871.data;

import org.apache.poi.ss.usermodel.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class SheetConfig {
    private static final DataFormatter FORMATTER = new DataFormatter();

    private final Sheet sheet;
    private final int headerRow;
    private int columnCount;
    private final Map<String, Integer> columnMap;

    public SheetConfig(Sheet sheet, int headerRow) {
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

    public int addRow() {
        return sheet.createRow(sheet.getLastRowNum() + 1).getRowNum() - headerRow - 1;
    }

    public void setCell(int rowNum, String column, boolean create, String value) {
        final Integer cellIndex = columnMap.get(column);
        if(cellIndex == null ) {
            throw new IllegalArgumentException("Column " + column + " does not exist");
        }

        final Row row = sheet.getRow(rowNum + headerRow + 1);
        if(row == null) {
            throw new IllegalArgumentException("Row " + rowNum + " does not exist");
        }

        Cell cell = row.getCell(cellIndex);
        if(cell == null) {
            if(create) {
                cell = row.createCell(cellIndex);
            } else {
                throw new IllegalArgumentException(("Cell does not exist and create not set"));
            }
        }

        cell.setCellValue(value);
    }

    public boolean columnExists(String columnName) {
        return columnMap.get(columnName) != null;
    }

    public void addColumn(String columnName) {
        columnMap.put(columnName, columnCount);
        columnCount++;
        setCell(-1, columnName, true, columnName);
    }
}

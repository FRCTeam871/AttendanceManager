package com.team871.data;

import org.apache.poi.ss.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class SheetConfig {
    private static final DataFormatter FORMATTER = new DataFormatter();

    private final Sheet sheet;
    private final int headerRow;
    private int columnCount = 0;
    private final Map<String, Integer> columnMap = new LinkedHashMap<>();

    public SheetConfig(@NotNull final Sheet sheet, final int headerRow) {
        this.sheet = sheet;
        this.headerRow = headerRow;
        final Row header = sheet.getRow(headerRow);
        this.columnCount = header.getLastCellNum();
        for (int ii = 0; ii < columnCount; ii++) {
            final Cell cell = header.getCell(ii);
            if (cell == null || cell.getCellType() == CellType.FORMULA) {
                continue;
            }

            columnMap.put(FORMATTER.formatCellValue(cell), ii);
        }
    }

    @Nullable
    public String getValue(final int row, @NotNull final String column) {
        final Integer cellIndex = columnMap.get(column);
        if (cellIndex == null) {
            return null;
        }

        return FORMATTER.formatCellValue(getRow(row).getCell(cellIndex));
    }

    @Nullable
    public Integer getIntValue(final int row, @NotNull final String column) {
        final Integer cellIndex = columnMap.get(column);
        if (cellIndex == null) {
            return null;
        }

        final Cell cell = getRow(row).getCell(cellIndex);
        if(cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                try {
                    return Integer.valueOf(cell.getStringCellValue());
                } catch (NumberFormatException ex) {
                    return null;
                }
            case NUMERIC:
                return (int) cell.getNumericCellValue();

            default:
                return null;
        }
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
        return getRow(row) != null;
    }

    public Row getRow(int row) {
        return sheet.getRow(row + headerRow + 1);
    }

    public int addRow() {
        return sheet.createRow(sheet.getLastRowNum() + 1).getRowNum() - headerRow - 1;
    }

    public void setCell(int rowNum, String column, String value) {
        final Integer cellIndex = columnMap.get(column);
        if (cellIndex == null) {
            throw new IllegalArgumentException("Column " + column + " does not exist");
        }

        final Row row = getRow(rowNum);
        if (row == null) {
            throw new IllegalArgumentException("Row " + rowNum + " does not exist");
        }

        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            cell = row.createCell(cellIndex);
        }

        cell.setCellValue(value);
    }

    public boolean columnExists(String columnName) {
        return columnMap.get(columnName) != null;
    }

    public void addColumn(String columnName) {
        columnMap.put(columnName, columnCount);
        columnCount++;
        setCell(-1, columnName, columnName);
    }
}

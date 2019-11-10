package com.team871.ui;

import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SheetWrapper implements MouseWheelListener {
    private static final Logger logger = LoggerFactory.getLogger(SheetWrapper.class);
    private static final Pattern inTimePattern = Pattern.compile("in(\\d+):(\\d+)");
    private static final DateFormat dateFormat = new SimpleDateFormat("H:mm");
    private static final int NAME_COL_WIDTH = 100;

    private static final int NUM_COLUMN = 0;
    private static final int LAST_NAME_COLUMN = 1;
    private static final int FIRST_NAME_COLUMN = 2;

    StudentTable table;

    private Font tableFont;

    private float destScroll = 0f;
    private float currScroll = 0f;
    private int cellHeight = 25;

    private Row highlightRow;
    private int highlightTimer;
    private int highlightTimerMax = 120;

    private int scrollTimer = 0;
    private int scrollAcc = 0;

    private boolean unsaved = false;
    private Rectangle dimension;
    private int maxScroll;

    public SheetWrapper(StudentTable table) {
        try {
            this.table = table;

            init();
        }catch(Exception e){
            e.printStackTrace();
        }

        tableFont = new Font("Arial", Font.BOLD, 12);
    }

    private void init() {
        updateDate();
    }

    public void tick(int time) {
        if(highlightTimer == 0) {
            highlightRow = null;
            highlightTimer = -1;
        }

        float speed;

        if(scrollTimer > 0) {
            destScroll += scrollAcc * -cellHeight;
            destScroll = cellHeight*(Math.round(destScroll/cellHeight)) - 1;
            scrollAcc = 0;
            speed = 5f;
            scrollTimer--;
        }else {
            if (highlightRow != null) {
                destScroll = -(cellHeight * (highlightRow.getRowNum() - 2));
                speed = 10f;
            } else {
                destScroll = -(int) (((Math.sin(time / (60f * 4)) + 1) / 2f) * maxScroll);
                speed = 20f;
            }
        }

        if(destScroll > 0) {
            destScroll = 0;
        }

        if(-destScroll > maxScroll) {
            destScroll = -maxScroll;
        }

        currScroll += (destScroll - currScroll) / speed;

        if(highlightTimer > 0) {
            highlightTimer--;
        }
    }

    Color CURRENT_DATE_COL = new Color(225, 210, 110);
    Color ABSENT_EVEN = new Color(0.7f, 0.35f, 0.35f);
    Color ABSENT_ODD =  new Color(1f, 0.4f, 0.4f);
    Color PRESENT_EVEN = new Color(0.3f, 0.65f, 0.3f);
    Color PRESENT_ODD = new Color(0.4f, 1f, 0.4f);

    String[][] cache;

    private int getCellWidth(int column, Graphics g) {
        if(column == 0) {
            return g.getFontMetrics().stringWidth(Integer.toString(table.getStudentCount())) + 10;
        }

        if(column == FIRST_NAME_COLUMN || column == LAST_NAME_COLUMN) {
            return NAME_COL_WIDTH;
        }

        return (int)((dimension.width - (NAME_COL_WIDTH * 2f)) / (table.getStudentCount() - FIRST_NAME_COLUMN));
    }

    public void drawTable(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.setFont(tableFont);

        int cy = cellHeight;

        final AffineTransform oTrans = g.getTransform();
        g.translate(0, currScroll);
        for (int r = 0; r <= table.getStudentCount(); r++) {
            drawRow(g, cy, 0, r);
            cy += cellHeight;
        }
        g.setTransform(oTrans);

        drawRow(g, 0, 0, -1);
    }

    private void drawRow(Graphics g, int cy, int cx, int r) {
        for (int i = 0; i <= table.getLastColumn(); i++) {
            final String cellVal = table.getValueAt(r, i);

            double hours = 0.0;
            if(cellVal != null && !cellVal.isEmpty()) {
                try {
                    hours = Double.parseDouble(cellVal);
                } catch(NumberFormatException e){}
            }

            boolean present = cellVal != null && hours > 0;
            boolean hasValue = cellVal != null && !cellVal.isEmpty();

            int cw = getCellWidth(i, g);
            int ch = cellHeight;

            g.setColor(r % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE);

            if(i == table.getCurrentDateColumn()) {
                if(Settings.getInstance().getLoginType() == LoginType.IN_OUT && cellVal != null && cellVal.startsWith("in")) {
                    g.setColor(Color.ORANGE);
                } else if(present) {
                    g.setColor(Color.GREEN);
                } else {
                    g.setColor(CURRENT_DATE_COL);
                }
            } else if(i > 1 && i < table.getCurrentDateColumn() && r > Settings.getInstance().getAttendanceHeaderRow()) {
                if(!hasValue) {
                    g.setColor(r % 2 == 0 ? ABSENT_EVEN : ABSENT_ODD);
                } else {
                    g.setColor(r % 2 == 0 ? PRESENT_EVEN : PRESENT_ODD);
                }
            }else if(i > table.getCurrentDateColumn() && i < table.getStudentCount() && r > 1) {
                g.setColor(Color.GRAY);
            }

            if(highlightRow != null && r == highlightRow.getRowNum()) {
                if(i <= FIRST_NAME_COLUMN) {
                    g.setColor(Color.YELLOW);
                } else if(i == table.getCurrentDateColumn()) {
                    g.fillRect(cx, cy, cw, ch);

                    int localMax = highlightTimerMax / 2;

                    int timer = highlightTimerMax - highlightTimer - highlightTimerMax/4;
                    if(timer < 0) timer = 0;
                    if(timer > localMax) timer = localMax;

                    float th = (timer)/(float)localMax * (float)Math.PI * 2f;
                    float a = (float)(-Math.cos(th)+1)/2f;

                    g.setColor(new Color(0f, 1f, 0f, a));
                } else if(i < table.getCurrentDateColumn()) {
                    g.fillRect(cx, cy, cw, ch);

                    int localMax = highlightTimerMax / 2;

                    float thruBar = (i-2f) / (table.getCurrentDateColumn()-2f);

                    int timer = highlightTimerMax - highlightTimer - (int)(thruBar*highlightTimerMax/4);
                    if(timer < 0) timer = 0;
                    if(timer > localMax) timer = localMax;

                    float th = (timer)/(float)localMax * (float)Math.PI * 2f;
                    float a = (float)(-Math.cos(th)+1)/2f;

                    g.setColor(new Color(0f, 1f, 0f, a / 2f));
                }
            }

            g.fillRect(cx, cy, cw, ch);
            g.setColor(Color.BLACK);
            g.drawRect(cx, cy, cw, ch);

            if(i > FIRST_NAME_COLUMN && i <= table.getCurrentDateColumn()) {
                if(r == Settings.getInstance().getAttendanceHeaderRow()) {
                    g.drawString(cellVal, cx + 4, cy + ch/2 + g.getFont().getSize()/2);
                } else if(Settings.getInstance().getLoginType() == LoginType.IN_OUT) {
                    String val = cellVal;
                    Matcher matcher = inTimePattern.matcher(val);
                    if(matcher.matches()) {
                        val = "In " + Integer.parseInt(matcher.group(1))%12 + ":" + matcher.group(2);
                    }

                    g.drawString(val, cx + 4, cy + ch/2 + g.getFont().getSize()/2);
                }
            } else if (hasValue) {
                g.drawString(cellVal, cx + 4, cy + ch/2 + g.getFont().getSize()/2);
            }

            cx += cw;
        }
    }

    public String sidToName(String sid) {
        for(int i = rosterHeader.getRowNum()+1; i <= rosterSheet.getPhysicalNumberOfRows(); i++) {
            final Row currentRow = rosterSheet.getRow(i);
            if(currentRow == null) continue;

            final Cell sidCell = currentRow.getCell(sidColumn);
            if(sidCell == null) continue;

            final String cellSidValue = formatCell(sidCell);
            if(cellSidValue == null || cellSidValue.isEmpty()) continue;

            if(cellSidValue.equals(sid)) {
                return getNameFromRow(currentRow, rosterFirstNameColumn, rosterLastNameColumn);
            }
        }

        return null;
    }

    private String getNameFromRow(Row r, int fNameColumn, int lNameColumn) {
        final Cell firstNameCell = r.getCell(fNameColumn);
        final Cell lastNameCell = r.getCell(lNameColumn);
        if(firstNameCell == null || lastNameCell == null) {
            return null;
        }

        return formatCell(firstNameCell) + " " + formatCell(lastNameCell);
    }

    private Row getRowByNameInternal(Sheet s, int headerRow, int fNameIdx, int lNameIdx, String first, String last) {
        if(first.isEmpty() || last.isEmpty()) return null;

        for(int i = headerRow + 1; i < s.getPhysicalNumberOfRows(); i++){
            final Row r = s.getRow(i);
            if(r == null) continue;

            final String fName = formatCell(r.getCell(fNameIdx));
            final String lName = formatCell(r.getCell(lNameIdx));
            if(first.equalsIgnoreCase(fName) && last.equalsIgnoreCase(lName)) {
                return r;
            }
        }

        return null;
    }

    public Row getRowBySID(String sid) {
        final String fullName = sidToName(sid);
        if(fullName == null || fullName.isEmpty()) {
            return null;
        }

        //TODO should null and size check this.
        final String[] parts = fullName.split("\\s+");

        return getRowByFullName(parts[0], parts[1]);
    }

    public Row getRowByFullName(String first, String last){
        return getRowByNameInternal(attendanceSheet, headerRow.getRowNum(), firstNameColumn, lastNameColumn, first, last);
    }

    private List<Row> getRowByLastNameInternal(Sheet s, int headerIdx, int lNameIdx, String lName) {
        if(lName.isEmpty())
            return Collections.emptyList();

        final List<Row> ret = new ArrayList<>();
        for(int i = headerIdx + 1; i < s.getPhysicalNumberOfRows(); i++) {
            Row r = s.getRow(i);
            if(r == null) continue;

            String maybeLastName = formatCell(r.getCell(lNameIdx));
            if(lName.equalsIgnoreCase(maybeLastName)) {
                ret.add(r);
            }
        }

        return ret;
    }

    public List<Row> getRowsByLastName(String name) {
        return getRowByLastNameInternal(attendanceSheet, headerRow.getRowNum(), lastNameColumn, name);
    }

    public void highlightRow(Row row) {
        highlightRow = row;
        highlightTimer = highlightTimerMax;
        scrollTimer = 0;
        scrollAcc = 0;
    }

    public String formatCell(Cell c) {
        return formatter.formatCellValue(c, eval);
    }

    public int getColumnIndexByName(String label) {
        for(int i = firstCol; i <= lastCol; i++) {
            Cell cell = headerRow.getCell(i);
            final String headerVal = formatCell(cell);
            if(headerVal != null && !headerVal.isEmpty()) {
                if(headerVal.equals(label)) return i;
            }
        }

        return -1;
    }

    private void setCell(Row r, int columnIdx, String value) {
        Cell c = r.getCell(columnIdx);
        if(c == null) {
            c = r.createCell(columnIdx);
        }
        c.setCellValue(value);
    }

    public boolean signInByLastName(String lastName){
        Row row = getRowsByLastName(lastName).get(0);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            setCell(row, currentDateColumn, "in" + dateFormat.format(new Date()));
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean signInByFullName(String firstName, String lastName){
        Row row = getRowByFullName(firstName, lastName);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            setCell(row, currentDateColumn, "in" + dateFormat.format(new Date()));
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean signOutBySID(String sid){
        if(!isSignedIn(sid)) return false;

        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && val.startsWith("in")){
                try {
                    Date inTime = dateFormat.parse(val.substring(2));
                    Date nowTime = dateFormat.parse(dateFormat.format(new Date()));

                    SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

                    long millis = nowTime.getTime() - inTime.getTime();
                    double hours = millis / 1000.0 / 60.0 / 60.0;
                    hours = ((int)Math.round(hours * 100.0) / 100.0);

                    System.out.println("IN: " + f.format(inTime));
                    System.out.println("NOW: " + f.format(nowTime));

                    System.out.println("MILLIS = " + millis);
                    System.out.println("HOURS = " + hours);

                    row.getCell(currentDateColumn).setCellValue(hours);
                }catch(ParseException e){
                    e.printStackTrace();
                }
            }
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean signOutByLastName(String lastName){
        if(!isSignedInByLastName(lastName)) return false;

        Row row = getRowsByLastName(lastName).get(0);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && val.startsWith("in")){
                try {
                    Date inTime = dateFormat.parse(val.substring(2));
                    Date nowTime = dateFormat.parse(dateFormat.format(new Date()));

                    SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

                    long millis = nowTime.getTime() - inTime.getTime();
                    double hours = millis / 1000.0 / 60.0 / 60.0;
                    hours = ((int)Math.round(hours * 100.0) / 100.0);

                    System.out.println("IN: " + f.format(inTime));
                    System.out.println("NOW: " + f.format(nowTime));

                    System.out.println("MILLIS = " + millis);
                    System.out.println("HOURS = " + hours);

                    row.getCell(currentDateColumn).setCellValue(hours);
                }catch(ParseException e){
                    e.printStackTrace();
                }
            }
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean signOutByFullName(String firstName, String lastName){
        if(!isSignedInByFullName(firstName, lastName)) return false;

        Row row = getRowByFullName(firstName, lastName);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && val.startsWith("in")){
                try {
                    Date inTime = dateFormat.parse(val.substring(2));
                    Date nowTime = dateFormat.parse(dateFormat.format(new Date()));

                    SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

                    long millis = nowTime.getTime() - inTime.getTime();
                    double hours = millis / 1000.0 / 60.0 / 60.0;
                    hours = ((int)Math.round(hours * 100.0) / 100.0);

                    System.out.println("IN: " + f.format(inTime));
                    System.out.println("NOW: " + f.format(nowTime));

                    System.out.println("MILLIS = " + millis);
                    System.out.println("HOURS = " + hours);

                    row.getCell(currentDateColumn).setCellValue(hours);
                }catch(ParseException e){
                    e.printStackTrace();
                }
            }
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean setPresentByLastName(String last, boolean present){
        Row row = getRowsByLastName(last).get(0);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            clearCacheRow(row.getRowNum());
            if(present){
                row.getCell(currentDateColumn).setCellValue(1);
            }else{
                row.getCell(currentDateColumn).setCellValue("");
            }
            return true;
        }

        return false;
    }

    public boolean setPresentByFullName(String first, String last, boolean present){
        Row row = getRowByFullName(first, last);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            clearCacheRow(row.getRowNum());
            if(present){
                row.getCell(currentDateColumn).setCellValue(1);
            }else{
                row.getCell(currentDateColumn).setCellValue("");
            }
            return true;
        }

        return false;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        scrollAcc = e.getWheelRotation();
        scrollTimer = 120;
    }

    public boolean save(File saveTo) {
        System.out.println("Saving attendance to " + saveTo.getAbsolutePath());
        try {
            saveTo.createNewFile(); //create the file if it doesn't exist
            FileOutputStream out = new FileOutputStream(saveTo);
            workbook.write(out); // write the workbook to the file
            out.close();
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        unsaved = false;
        return true;
    }

    public File getFile() {
        return this.file;
    }

    public boolean hasUnsaved() {
        return unsaved;
    }

    public boolean checkValidDate(String date) {
        return getColumnIndexByName(date) != -1;
    }
    public void updateDate() {
        currentDateColumn = getColumnIndexByName(Settings.getInstance().getDate());
    }

    public void showNotSignedOutDialog() {
        boolean anyoneNotSignedOut = false;

        for(int i = headerRow.getRowNum() + 1; i < attendanceSheet.getPhysicalNumberOfRows(); i++){
            Row r = attendanceSheet.getRow(i);
            if(r == null) continue;
            Cell cell = r.getCell(currentDateColumn);
            try {
                String val = formatCell(cell);
                if (val != null && val.startsWith("in")) {
                    anyoneNotSignedOut = true;
                    break;
                }
            } catch (Exception ignored){}
        }

        if(anyoneNotSignedOut) {
            int result = JOptionPane.showConfirmDialog(null, "There are people that haven't signed out.\nDo you want to sign them out?\n(If not, sign in time will be saved)", "Attendance Manager", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

            switch(result){
                case JOptionPane.YES_OPTION:
                    out: for(int i = headerRow.getRowNum() + 1; i < attendanceSheet.getPhysicalNumberOfRows(); i++){
                        Row r = attendanceSheet.getRow(i);
                        if(r == null) continue;
                        Cell cell = r.getCell(currentDateColumn);
                        try{
                            String val = formatCell(cell);
                            if(val != null && val.startsWith("in")){
                                signOutBySID(formatCell(r.getCell(lastCol)));
                            }
                        }catch(IllegalStateException e){
                            e.printStackTrace(); // "value changed"
                        }
                    }
                    break;
                case JOptionPane.NO_OPTION:

                    break;
                default: // cancel or x-out
                    // don't do anything
                    break;
            }
        }
    }

    public void setDimension(Rectangle dimension) {
        this.dimension = dimension;
        this.maxScroll = Math.max(0, (cellHeight * (maxRow - headerRow.getRowNum() + 1)) - dimension.height + 2);
    }
}

package com.team871.ui;

import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.*;

import javax.swing.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
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
    private static final Pattern inTimePattern = Pattern.compile("in(\\d+):(\\d+)");
    private static final DateFormat dateFormat = new SimpleDateFormat("H:mm");

    private File file;

    private Workbook workbook;
    private FormulaEvaluator eval;
    private DataFormatter formatter;
    private Font tableFont;
    private int firstRow;
    private int lastRow;
    private int maxRow;

    private Sheet attendanceSheet;
    private Row headerRow;
    private int lastNameColumn = -1;
    private int firstNameColumn = -1;

    private Sheet rosterSheet;
    private Row rosterHeader;
    private int sidColumn = -1;
    private int rosterLastNameColumn = -1;
    private int rosterFirstNameColumn = -1;


    private float destScroll = 0f;
    private float currScroll = 0f;
    private int renderHeight;
    private int cellHeight = 25;

    Row highlightRow;
    private int highlightTimer;
    private int highlightTimerMax = 120;

    private int currentDateColumn;

    private int scrollTimer = 0;
    private int scrollAcc = 0;

    private boolean unsaved = false;


    public SheetWrapper(Path u) {
        try {
            System.out.println("Loading attendanceSheet from URL: " + u);
            this.file = u.toFile();
            System.out.println("Loading File: " + file.getAbsolutePath());
            InputStream is = new FileInputStream(file);
            workbook = WorkbookFactory.create(is);

            eval = workbook.getCreationHelper().createFormulaEvaluator();
            formatter = new DataFormatter();

            System.out.println("Available Workbook Sheets:");
            for(int i = 0; i < workbook.getNumberOfSheets(); i++){
                System.out.println(workbook.getSheetName(i));
            }

            String using = Settings.getSheet();
            System.out.printf("Using Sheet: \"%s\"\n", using);

            attendanceSheet = workbook.getSheet(using);
            rosterSheet = workbook.getSheet(Settings.getRosterSheet());

            init();
        }catch(Exception e){
            e.printStackTrace();
        }

        tableFont = new Font("Arial", Font.BOLD, 12);

        updateDate();
    }

    private void init() {
        headerRow = attendanceSheet.getRow(1);

        configureRosterSheet();
        configureAttendenceSheet();

        firstRow = -1;
        lastRow = -1;
        for(int i = 0; i <= headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            final String headerVal = formatCell(cell);
            if(headerVal != null && !headerVal.isEmpty()) {
                if(firstRow == -1){
                    firstRow = i;
                }
                if(i > lastRow) lastRow = i;
            }
        }

        maxRow = -1;

        for(int i = headerRow.getRowNum(); i < attendanceSheet.getPhysicalNumberOfRows(); i++){
            if(attendanceSheet.getRow(i) != null) {
                String headerVal = formatCell(attendanceSheet.getRow(i).getCell(firstRow));
                if (headerVal != null && !headerVal.isEmpty()) {
                    maxRow = i;
                }
            }
        }

        clearCache();
    }

    private void configureRosterSheet() {
        rosterHeader = rosterSheet.getRow(1);

        for(int i = 0; i <= rosterHeader.getLastCellNum(); i++) {
            Cell cell = rosterHeader.getCell(i);
            final String headerVal = formatCell(cell);
            if("SID".equalsIgnoreCase(headerVal)) {
                sidColumn = i;

            } else if("Last".equalsIgnoreCase(headerVal)) {
                rosterLastNameColumn = i;
            } else if("First".equalsIgnoreCase(headerVal)) {
                rosterFirstNameColumn = i;
            }
        }

        if(sidColumn < 0) {
            sidColumn = headerRow.getLastCellNum() + 1;

            headerRow.createCell(sidColumn).setCellValue("SID");

            for (int i = rosterHeader.getRowNum() + 1; i < rosterSheet.getPhysicalNumberOfRows(); i++) {
                if (rosterSheet.getRow(i) != null) {
                    rosterSheet.getRow(i).createCell(sidColumn).setCellValue("");
                }
            }
        }

        if(sidColumn < 0 || rosterFirstNameColumn < 0 || rosterLastNameColumn < 0) {
            throw new IllegalStateException("Sheet is missing first/last/sid columns in roster");
        }
    }

    private void configureAttendenceSheet() {
        headerRow = attendanceSheet.getRow(1);
        for(int i = 0; i <= headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            final String headerVal = formatCell(cell);
            if("Last".equalsIgnoreCase(headerVal)) {
                lastNameColumn = i;
            } else if("First".equalsIgnoreCase(headerVal)) {
                firstNameColumn = i;
            }
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

    public String nameToSid(String name) {
        final String[] parts = name.trim().split("\\s+");
        if(parts.length != 2) {
            throw new IllegalArgumentException("The name doesn't have two parts!");
        }

        for(int i = rosterHeader.getRowNum() + 1; i <= rosterSheet.getPhysicalNumberOfRows(); i++) {
            final Row currentRow = rosterSheet.getRow(i);
            final String maybeName = getNameFromRow(currentRow, rosterFirstNameColumn, rosterLastNameColumn);

            if(name.equalsIgnoreCase(maybeName)) {
                final Cell sidCell = currentRow.getCell(sidColumn);
                if(sidCell == null) {
                    break;
                }

                final String sid = formatCell(sidCell);
                return sid == null || sid.isEmpty() ? null: sid;
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


    public void tick(int time){
        int maxScroll = (cellHeight * (maxRow - headerRow.getRowNum())) - renderHeight + 2;
//        System.out.println(renderHeight);

        if(highlightTimer == 0){
            highlightRow = null;
            highlightTimer = -1;
        }

        float speed;

        if(scrollTimer > 0){
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

        if(destScroll > 0) destScroll = 0;
        if(-destScroll > maxScroll) destScroll = -maxScroll;

        currScroll += (destScroll - currScroll) / speed;

        if(highlightTimer > 0) highlightTimer--;
    }

    Color CURRENT_DATE_COL = new Color(225, 210, 110);
    Color ABSENT_EVEN = new Color(0.7f, 0.35f, 0.35f);
    Color ABSENT_ODD =  new Color(1f, 0.4f, 0.4f);
    Color PRESENT_EVEN = new Color(0.3f, 0.65f, 0.3f);
    Color PRESENT_ODD = new Color(0.4f, 1f, 0.4f);

    String[][] cache;

    public void drawTable(Graphics2D g, int width, int height, int time){

        //TODO: this is kinda hacky
        int renderWidth = width;
        this.renderHeight = height;

//        g.setColor(Color.BLUE);
//        g.drawRect(10, 10, 20, 20);

//        g.fillRect(0, 0, width, height);

        int startingRow = headerRow.getRowNum();

        float nameColWidth = 100f;

        boolean showSIDCol = true;

        float cellW = (width - nameColWidth*2f) / ((lastRow - (showSIDCol ? 0 : 1)) - (firstRow+2) + 1f);

        int ofsY = (int)currScroll;

        AffineTransform oTrans = g.getTransform();

        g.setColor(Color.BLACK);
        g.setFont(tableFont);
        // bottom to top
        r: for(int r = maxRow-1; r >= startingRow; r--) {
            g.setTransform(oTrans);
            if(r > startingRow) g.translate(0, ofsY);
            int y = r - startingRow;

            for (int i = firstRow; i <= lastRow - (showSIDCol ? 0 : 1); i++) {
                int x = i - firstRow;

                final String headerVal = fetchCached(r, i);

                double hours = 0.0;
                if(headerVal != null && !headerVal.isEmpty()){
                    try{
                        hours = Double.parseDouble(headerVal);
                    }catch(NumberFormatException e){}
                }
                boolean present = headerVal != null && hours > 0;

                boolean hasValue = headerVal != null && !headerVal.isEmpty();

                int cx;
                if(i <= firstRow + 1){
                    cx = (int)(x * nameColWidth);
                }else{
                    cx = (int)(nameColWidth * 2 + (x-2)*cellW);
                }
                int cy = y * cellHeight;
                int cw = (i <= firstRow + 1) ? (int)nameColWidth : (int)cellW;
                int ch = cellHeight;

                g.setColor(r % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE);

                if(i == currentDateColumn){
                    if(Settings.getMode() == Mode.IN_OUT && headerVal != null && headerVal.startsWith("in")){
                        g.setColor(Color.ORANGE);
                    }else if(present){
                        g.setColor(Color.GREEN);
                    }else {
                        g.setColor(CURRENT_DATE_COL);
                    }
                }else if(i > firstRow + 1 && i < currentDateColumn && r > startingRow){
                    if(!hasValue) {
                        g.setColor(r % 2 == 0 ? ABSENT_EVEN : ABSENT_ODD);
                    }else{
                        g.setColor(r % 2 == 0 ? PRESENT_EVEN : PRESENT_ODD);
                    }
                }else if(i > currentDateColumn && i < lastRow - 1 && r > 1){
                    g.setColor(Color.GRAY);
                }

                if(highlightRow != null && r == highlightRow.getRowNum()){
                    if(i <= firstRow + 1){
                        g.setColor(Color.YELLOW);
                    }else if(i == currentDateColumn){
                        g.fillRect(cx, cy, cw, ch);

                        int localMax = highlightTimerMax / 2;

                        int timer = highlightTimerMax - highlightTimer - highlightTimerMax/4;
                        if(timer < 0) timer = 0;
                        if(timer > localMax) timer = localMax;

                        float th = (timer)/(float)localMax * (float)Math.PI * 2f;
                        float a = (float)(-Math.cos(th)+1)/2f;

                        g.setColor(new Color(0f, 1f, 0f, a));
                    }else if(i < currentDateColumn){
                        g.fillRect(cx, cy, cw, ch);

                        int localMax = highlightTimerMax / 2;

                        float thruBar = (i-2f) / (float)(currentDateColumn-2f);

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

                if(i > firstRow + 1 && i <= currentDateColumn && r > startingRow){
                    if(Settings.getMode() == Mode.IN_OUT){

                        String val = headerVal;

                        Matcher matcher = inTimePattern.matcher(val);
                        if(matcher.matches()) {
                            val = "In " + Integer.parseInt(matcher.group(1))%12 + ":" + matcher.group(2);
                        }

                        g.drawString(val, cx + 4, cy + ch/2 + g.getFont().getSize()/2);
                    }
                }else{
                    if (hasValue) {
                        g.drawString(headerVal, cx + 4, cy + ch/2 + g.getFont().getSize()/2);
                    }
                }

            }
        }
    }

    public void setSIDByFullName(String firstName, String lastName, String sid) {
        Row row = getRowByNameInternal(rosterSheet, rosterHeader.getRowNum(), rosterFirstNameColumn, rosterLastNameColumn, firstName, lastName);
        if(row == null) {
            throw new IllegalArgumentException(firstName + " " + lastName + " was not found!");
        }

        if(row.getCell(lastRow) == null) {
            row.createCell(lastRow);
        }

        row.getCell(lastRow).setCellType(CellType.STRING);
        row.getCell(lastRow).setCellValue(sid);
        clearCacheRow(row.getRowNum());
    }

    // TODO This should take a full name, or at least assert that the array is size 1
    public void setSIDByLastName(String lastName, String sid) {
        Row row = getRowByLastNameInternal(rosterSheet, rosterHeader.getRowNum(), rosterLastNameColumn, lastName).get(0);
        if(row.getCell(sidColumn) == null){
            row.createCell(sidColumn);
        }

        row.getCell(sidColumn).setCellType(CellType.STRING);
        row.getCell(sidColumn).setCellValue(sid);
        clearCacheRow(row.getRowNum());
    }

    private String fetchCached(int r, int i) {

        if(cache == null){
            return formatCell(attendanceSheet.getRow(r).getCell(i));
        }

        int cr = r - headerRow.getRowNum();
        int ci = i - firstRow;
        if(cache[cr][ci] != null) return cache[cr][ci];
        return cache[cr][ci] = formatCell(attendanceSheet.getRow(r).getCell(i));
    }

    private void clearCache(){
        cache = new String[maxRow - headerRow.getRowNum() + 1][];
        for(int i = 0; i < cache.length; i++){
            cache[i] = new String[lastRow - firstRow + 1];
        }

        for(int rn = maxRow-1; rn >= headerRow.getRowNum(); rn--) {
            for (Cell c : attendanceSheet.getRow(rn)) {
                if (c.getCellType() == CellType.FORMULA) {
                    eval.evaluateFormulaCell(c);
                }
            }
        }
    }

    private void clearCacheRow(int r){
        cache[r - headerRow.getRowNum()] = new String[lastRow - firstRow + 1];

        for (Cell c : attendanceSheet.getRow(r)) {
            if (c.getCellType() == CellType.FORMULA) {
                eval.evaluateFormulaCell(c);
            }
        }
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

    public Row getRowWithNoSIDByFullName(String first, String last) {
        if(first.isEmpty() || last.isEmpty())
            return null;

        if(nameToSid(first + " " + last) != null) {
            return null;
        }

        return getRowByFullName(first, last);
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

    public List<Row> getRowWithNoSIDByLastName(String name) {
        List<Row> ret = getRowByLastNameInternal(attendanceSheet, headerRow.getRowNum(), lastNameColumn, name);

        //TODO: There is a potential bug here if the rows are out of order between the two sheets
        for(int i = ret.size()-1; i >= 0; i--) {
            final String maybeSid = nameToSid(formatCell(ret.get(i).getCell(firstNameColumn)) + " " + name);

            // Throw away anyone who'se already got an SID
            if(maybeSid != null && !maybeSid.isEmpty()) {
                ret.remove(i);
            }
        }

        return ret;
    }

    public void highlightRow(Row row){
        highlightRow = row;
        highlightTimer = highlightTimerMax;
        scrollTimer = 0;
        scrollAcc = 0;
    }

    public String formatCell(Cell c) {
        return formatter.formatCellValue(c, eval);
    }

    public int getColumnIndexByName(String label) {
        for(int i = firstRow; i <= lastRow; i++) {
            Cell cell = headerRow.getCell(i);
            final String headerVal = formatCell(cell);
            if(headerVal != null && !headerVal.isEmpty()) {
                if(headerVal.equals(label)) return i;
            }
        }

        return -1;
    }

    public boolean setPresent(String sid, boolean present){
        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            unsaved = true;

            //System.out.println("IT WAS " + formatCell(row.getCell(currentDateColumn)));
            if(present){
                row.getCell(currentDateColumn).setCellValue(1);
            }else{
                row.getCell(currentDateColumn).setCellValue("");
            }
            //System.out.println("THE OUTPUT IS " + formatCell(row.getCell(currentDateColumn)));
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean isSignedIn(String sid){
        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && val.startsWith("in")){
                return true;
            }
        }
        return false;
    }

    public boolean isSignedInByLastName(String lastName){
        Row row = getRowsByLastName(lastName).get(0);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && val.startsWith("in")){
                return true;
            }
        }
        return false;
    }

    public boolean isSignedInByFullName(String firstName, String lastName){
        Row row = getRowByFullName(firstName, lastName);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && val.startsWith("in")){
                return true;
            }
        }
        return false;
    }


    public boolean isSignedOut(String sid){
        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && !val.startsWith("in") && !val.isEmpty()){
                return true;
            }
        }
        return false;
    }

    public boolean isSignedOutByLastName(String lastName){
        Row row = getRowsByLastName(lastName).get(0);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && !val.startsWith("in") && !val.isEmpty()){
                return true;
            }
        }
        return false;
    }

    public boolean isSignedOutByFullName(String firstName, String lastName){
        Row row = getRowByFullName(firstName, lastName);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            if(val != null && !val.startsWith("in") && !val.isEmpty()){
                return true;
            }
        }
        return false;
    }

    public boolean signInBySID(String sid){
        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            row.getCell(currentDateColumn).setCellValue("in" + dateFormat.format(new Date()));
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean signInByLastName(String lastName){
        Row row = getRowsByLastName(lastName).get(0);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            row.getCell(currentDateColumn).setCellValue("in" + dateFormat.format(new Date()));
            clearCacheRow(row.getRowNum());
            return true;
        }

        return false;
    }

    public boolean signInByFullName(String firstName, String lastName){
        Row row = getRowByFullName(firstName, lastName);
        if(row != null && currentDateColumn != -1){
            unsaved = true;
            row.getCell(currentDateColumn).setCellValue("in" + dateFormat.format(new Date()));
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

    public boolean isPresent(String sid){
        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));
            return val != null && !val.isEmpty();
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
        }catch(Exception e) {
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
        currentDateColumn = getColumnIndexByName(Settings.getDate());
    }

    public void showNotSignedOutDialog() {
        boolean anyoneNotSignedOut = false;

        out: for(int i = headerRow.getRowNum() + 1; i < attendanceSheet.getPhysicalNumberOfRows(); i++){
            Row r = attendanceSheet.getRow(i);
            if(r == null) continue;
            for(int c = firstRow + 1; c < lastRow-1; c++){
                Cell cell = r.getCell(c);
                String val = formatCell(cell);
                if(val != null && val.startsWith("in")){
                    anyoneNotSignedOut = true;
                    break out;
                }
            }
        }

        if(anyoneNotSignedOut){
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
                                signOutBySID(formatCell(r.getCell(lastRow)));
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
}

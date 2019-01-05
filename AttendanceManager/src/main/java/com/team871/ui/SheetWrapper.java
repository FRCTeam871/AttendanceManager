package com.team871.ui;

import org.apache.poi.ss.usermodel.*;
import com.team871.util.Settings;

import javax.swing.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SheetWrapper implements MouseWheelListener {

    private File file;

    private Workbook workbook;
    private Sheet sheet;
    private FormulaEvaluator eval;
    private DataFormatter formatter;
    private Font tableFont;
    private int firstRow;
    private int lastRow;
    private Row headerRow;
    private int maxRow;

    private float destScroll = 0f;
    private float currScroll = 0f;
    private int renderWidth;
    private int renderHeight;
    private int cellHeight = 25;

    Row highlightRow;
    private int highlightTimer;
    private int highlightTimerMax = 120;

    private int currentDateColumn;

    private int scrollTimer = 0;
    private int scrollAcc = 0;

    private boolean unsaved = false;

    DateFormat dateFormat = new SimpleDateFormat("H:mm");

    Pattern inTimePattern = Pattern.compile("in(\\d+):(\\d+)");

    public SheetWrapper(URL u){

        try{
            System.out.println("Loading sheet from URL: " + u);
            this.file = new File(u.toURI());
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

            sheet = workbook.getSheet(using);

            init();

            setupSIDColumn(sheet, file);

        }catch(Exception e){
            e.printStackTrace();
        }

        tableFont = new Font("Arial", Font.BOLD, 12);

        updateDate();
    }

    private void init(){

        headerRow = sheet.getRow(1);

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

        for(int i = headerRow.getRowNum(); i < sheet.getPhysicalNumberOfRows(); i++){
            if(sheet.getRow(i) != null) {
                String headerVal = formatCell(sheet.getRow(i).getCell(firstRow));
                if (headerVal != null && !headerVal.isEmpty()) {
                    maxRow = i;
                }
            }
        }

        clearCache();

    }

    private void setupSIDColumn(Sheet sheet, File f) {

        String testSIDColumn = formatCell(headerRow.getCell(lastRow));
        if(testSIDColumn != null && testSIDColumn.equals("SID")) return;

        headerRow.createCell(lastRow + 1).setCellValue("SID");

        for(int i = 2; i < sheet.getPhysicalNumberOfRows(); i++){
            if(sheet.getRow(i) != null) {
                String headerVal = formatCell(sheet.getRow(i).getCell(firstRow));
                if (headerVal != null && !headerVal.isEmpty()) {
                    sheet.getRow(i).createCell(lastRow + 1).setCellValue("");
                }
            }
        }
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
        this.renderWidth = width;
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

    public void setSIDByFullName(String firstName, String lastName, String sid){
        Row row = getRowByFullName(firstName, lastName);
        if(row.getCell(lastRow) == null){
            row.createCell(lastRow);
        }
        row.getCell(lastRow).setCellType(CellType.STRING);
        row.getCell(lastRow).setCellValue(sid);
        clearCacheRow(row.getRowNum());
    }

    public void setSIDByLastName(String lastName, String sid){
        Row row = getRowByLastName(lastName).get(0);
        if(row.getCell(lastRow) == null){
            row.createCell(lastRow);
        }
        row.getCell(lastRow).setCellType(CellType.STRING);
        row.getCell(lastRow).setCellValue(sid);
        clearCacheRow(row.getRowNum());
    }

    public String getFullnameBySID(String sid){
        Row row = getRowBySID(sid);
        if(row == null) return "";
        return formatCell(row.getCell(firstRow + 1)) + " " + formatCell(row.getCell(firstRow));
    }

    private String fetchCached(int r, int i) {

        if(cache == null){
            return formatCell(sheet.getRow(r).getCell(i));
        }

        int cr = r - headerRow.getRowNum();
        int ci = i - firstRow;
        if(cache[cr][ci] != null) return cache[cr][ci];
        return cache[cr][ci] = formatCell(sheet.getRow(r).getCell(i));
    }

    private void clearCache(){
        cache = new String[maxRow - headerRow.getRowNum() + 1][];
        for(int i = 0; i < cache.length; i++){
            cache[i] = new String[lastRow - firstRow + 1];
        }

        for(int rn = maxRow-1; rn >= headerRow.getRowNum(); rn--) {
            for (Cell c : sheet.getRow(rn)) {
                if (c.getCellType() == CellType.FORMULA) {
                    eval.evaluateFormulaCell(c);
                }
            }
        }
    }

    private void clearCacheRow(int r){
        cache[r - headerRow.getRowNum()] = new String[lastRow - firstRow + 1];

        for (Cell c : sheet.getRow(r)) {
            if (c.getCellType() == CellType.FORMULA) {
                eval.evaluateFormulaCell(c);
            }
        }
    }

    public Row getRowBySID(String sid){
        for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
            if(r == null) continue;
            String headerVal = formatCell(r.getCell(lastRow));
            if(headerVal != null && headerVal.equals(sid)){
                return r;
            }
        }
        return null;
    }

    public Row getRowByFullName(String first, String last){
        if(first.isEmpty() || last.isEmpty()) return null;
        for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
            if(r == null) continue;
            String headerVal = formatCell(r.getCell(firstRow+1));
            String headerVal2 = formatCell(r.getCell(firstRow));
            if((headerVal != null && headerVal.equalsIgnoreCase(first)) && (headerVal2 != null && headerVal2.equalsIgnoreCase(last))){
                return r;
            }
        }
        return null;
    }

    public Row getRowWithNoSIDByFullName(String first, String last){
        if(first.isEmpty() || last.isEmpty()) return null;
        for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
            if(r == null) continue;
            String headerVal = formatCell(r.getCell(firstRow+1));
            String headerVal2 = formatCell(r.getCell(firstRow));
            boolean hasSIDAlready = r.getCell(lastRow) != null && !formatCell(r.getCell(lastRow)).isEmpty();
            if((headerVal != null && headerVal.equalsIgnoreCase(first)) && (headerVal2 != null && headerVal2.equalsIgnoreCase(last)) && !hasSIDAlready){
                return r;
            }
        }
        return null;
    }

    public List<Row> getRowByLastName(String name){
        List<Row> ret = new ArrayList<>();
        if(name.isEmpty()) return ret;
        for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
            if(r == null) continue;
            String headerVal = formatCell(r.getCell(firstRow));
            if(headerVal != null && headerVal.equalsIgnoreCase(name)){
                ret.add(r);
            }
        }
        return ret;
    }

    public List<Row> getRowWithNoSIDByLastName(String name){
        List<Row> ret = new ArrayList<>();
        if(name.isEmpty()) return ret;
        for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
            if(r == null) continue;
            String headerVal = formatCell(r.getCell(firstRow));
            //System.out.println("cell = " + r.getCell(lastRow) + "");
            boolean hasSIDAlready = r.getCell(lastRow) != null && !formatCell(r.getCell(lastRow)).isEmpty();
            if(headerVal != null && headerVal.equalsIgnoreCase(name) && !hasSIDAlready){
                //System.out.println("previous val = \"" + formatCell(r.getCell(lastRow)) + "\"");
                ret.add(r);
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

    public Sheet getSheet(){
        return sheet;
    }

    public int getColumnIndexByName(String label){
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
        Row row = getRowByLastName(lastName).get(0);
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
        Row row = getRowByLastName(lastName).get(0);
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
        Row row = getRowByLastName(lastName).get(0);
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

        Row row = getRowByLastName(lastName).get(0);
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
        Row row = getRowByLastName(last).get(0);
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

    public double getHours(String sid){
        Row row = getRowBySID(sid);
        if(row != null && currentDateColumn != -1){
            String val = formatCell(row.getCell(currentDateColumn));

            if(val != null){
                try{
                    return Double.parseDouble(val);
                } catch(NumberFormatException e){
                    return 0.0;
                }
            }
        }
        return 0.0;
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

        out: for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
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
                    out: for(int i = headerRow.getRowNum() + 1; i < sheet.getPhysicalNumberOfRows(); i++){
                        Row r = sheet.getRow(i);
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

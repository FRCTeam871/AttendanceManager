package ui;

import org.apache.poi.ss.usermodel.*;

import javax.swing.*;
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
import java.util.Random;

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

            String using = "Pre-Season";
            System.out.printf("Using Sheet: \"%s\"\n", using);

            sheet = workbook.getSheet(using);

            init();

            setupSIDColumn(sheet, file);

        }catch(Exception e){
            e.printStackTrace();
        }

        tableFont = new Font("Arial", Font.BOLD, 12);

        currentDateColumn = getColumnIndexByName("10/25");
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
            String headerVal = formatCell(sheet.getRow(i).getCell(firstRow));
            if(headerVal != null && !headerVal.isEmpty()){
                maxRow = i;
            }
        }
    }

    private void setupSIDColumn(Sheet sheet, File f) {

        String testSIDColumn = formatCell(headerRow.getCell(lastRow));
        if(testSIDColumn != null && testSIDColumn.equals("SID")) return;

        headerRow.createCell(lastRow + 1).setCellValue("SID");

        for(int i = 2; i < sheet.getPhysicalNumberOfRows(); i++){
            String headerVal = formatCell(sheet.getRow(i).getCell(firstRow));
            if(headerVal != null && !headerVal.isEmpty()){
                sheet.getRow(i).createCell(lastRow + 1).setCellValue("");
            }
        }
    }

    public void tick(int time){
        int maxScroll = (cellHeight * (maxRow - headerRow.getRowNum())) - renderHeight + 2;

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

    public void drawTable(Graphics2D g, int width, int height, int time){
        //TODO: this is kinda hacky
        this.renderWidth = width;
        this.renderHeight = height;

        g.setColor(Color.BLUE);
        g.drawRect(10, 10, 20, 20);

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
            for (int i = firstRow; i <= lastRow - (showSIDCol ? 0 : 1); i++) {
                g.setColor(Color.BLACK);
                int x = i - firstRow;
                int y = r - startingRow;

                Row row = sheet.getRow(r);
                Cell cell = row.getCell(i);
                final String headerVal = formatCell(cell);

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
                    if(headerVal != null && headerVal.equals("1")){
                        g.setColor(new Color(0f, 1f, 0f));
                    }else {
                        g.setColor(new Color(225, 210, 110));
                    }
                }else if(i > firstRow + 1 && i < currentDateColumn && r > startingRow){
                    if((headerVal == null || headerVal.isEmpty())) {
                        g.setColor(r % 2 == 0 ? new Color(0.7f, 0.35f, 0.35f) : new Color(1f, 0.4f, 0.4f));
                    }else{
                        g.setColor(r % 2 == 0 ? new Color(0.3f, 0.65f, 0.3f) : new Color(0.4f, 1f, 0.4f));
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

                }else{
                    if (headerVal != null && !headerVal.isEmpty()) {
                        g.drawString(headerVal, cx + 4, cy + ch/2 + g.getFont().getSize()/2);
                    }
                }

            }
        }
    }

    public Row getRowBySID(String sid){
        for(int i = headerRow.getRowNum(); i < sheet.getPhysicalNumberOfRows(); i++){
            Row r = sheet.getRow(i);
            String headerVal = formatCell(r.getCell(lastRow));
            if(headerVal != null && headerVal.equals(sid)){
                return r;
            }
        }
        return null;
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
        if(row != null){
            unsaved = true;
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
        if(row != null){
            String val = formatCell(row.getCell(currentDateColumn));
            return val != null && val.equals("1");
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
}
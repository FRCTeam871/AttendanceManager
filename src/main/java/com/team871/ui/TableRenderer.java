package com.team871.ui;

import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TableRenderer implements MouseWheelListener {
    private static final Logger logger = LoggerFactory.getLogger(TableRenderer.class);
    private static final Pattern inTimePattern = Pattern.compile("in(\\d+):(\\d+)");
    private static final int NAME_COL_WIDTH = 100;

    private static final int NUM_COLUMN = 0;
    private static final int LAST_NAME_COLUMN = 1;
    private static final int FIRST_NAME_COLUMN = 2;

    private static final Color CURRENT_DATE_COL = new Color(225, 210, 110);
    private static final Color ABSENT_EVEN = new Color(0.7f, 0.35f, 0.35f);
    private static final Color ABSENT_ODD =  new Color(1f, 0.4f, 0.4f);
    private static final Color PRESENT_EVEN = new Color(0.3f, 0.65f, 0.3f);
    private static final Color PRESENT_ODD = new Color(0.4f, 1f, 0.4f);

    private final StudentTable table;

    private Font tableFont;

    private float destScroll = 0f;
    private float currScroll = 0f;
    private int cellHeight = 25;

    private Row highlightRow;
    private int highlightTimer;
    private int highlightTimerMax = 120;

    private int scrollTimer = 0;
    private int scrollAcc = 0;

    private Rectangle dimension;
    private int maxScroll;

    public TableRenderer(StudentTable table) {
        this.table = table;
        tableFont = new Font("Arial", Font.BOLD, 12);
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

    public void highlightRow(Row row) {
        highlightRow = row;
        highlightTimer = highlightTimerMax;
        scrollTimer = 0;
        scrollAcc = 0;
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

    private int getCellWidth(int column, Graphics g) {
        if(column == 0) {
            return g.getFontMetrics().stringWidth(Integer.toString(table.getStudentCount())) + 10;
        }

        if(column == FIRST_NAME_COLUMN || column == LAST_NAME_COLUMN) {
            return NAME_COL_WIDTH;
        }

        return (int)((dimension.width - (NAME_COL_WIDTH * 2f)) / (table.getStudentCount() - FIRST_NAME_COLUMN));
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

    public void maybeShowNotSignedOutDialog() {
        if(table.areAllSignedOut()) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(null, "There are people that haven't signed out.\nDo you want to sign them out?\n(If not, sign in time will be saved)", "Attendance Manager", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if(result == JOptionPane.YES_OPTION) {
            table.forceSignOut();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        scrollAcc = e.getWheelRotation();
        scrollTimer = 120;
    }

    public void setDimension(Rectangle dimension) {
        this.dimension = dimension;
        this.maxScroll = Math.max(0, (cellHeight * (table.getStudentCount() + 1)) - dimension.height + 2);
    }
}

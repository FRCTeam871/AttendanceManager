package com.team871.ui;

import com.team871.data.Student;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class TableRenderer implements MouseWheelListener {
    private static final int NAME_COL_WIDTH = 100;

    private static final Color CURRENT_DATE_COL = new Color(225, 210, 110);
    private static final Color ABSENT_EVEN = new Color(0.7f, 0.35f, 0.35f);
    private static final Color ABSENT_ODD =  new Color(1f, 0.4f, 0.4f);
    private static final Color PRESENT_EVEN = new Color(0.3f, 0.65f, 0.3f);
    private static final Color PRESENT_ODD = new Color(0.4f, 1f, 0.4f);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");

    private final StudentTable table;

    private Font tableFont;

    private float destScroll = 0f;
    private float currScroll = 0f;
    private int cellHeight = 25;
    private int indexColumnWidth = 0;
    private int attendanceColumnWidth = 0;

    private Student highlightStudent;
    private int highlightTimer;
    private int highlightTimerMax = 120;

    private int scrollTimer = 0;
    private int scrollAcc = 0;

    private Rectangle dimension;
    private int maxScroll;

    public TableRenderer(StudentTable table) {
        this.table = table;
        tableFont = new Font("Arial", Font.BOLD, 12);

        table.addListener(new StudentTable.Listener() {
            @Override
            public void onSignIn(Student student) {
                highlightStudent(student);
            }

            @Override
            public void onSignOut(Student student) {
                highlightStudent(student);
            }
        });
    }

    public void tick(int time) {
        if(highlightTimer == 0) {
            highlightStudent = null;
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
            if (highlightStudent != null) {
                destScroll = -(cellHeight * (highlightStudent.getAttendanceRow().getRowNum() - 2));
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

    public void drawTable(Graphics2D g) {
        if(indexColumnWidth <= 0) {
            indexColumnWidth = g.getFontMetrics().stringWidth(Integer.toString(table.getStudentCount())) + 10;
        }

        if(attendanceColumnWidth <= 0) {
            attendanceColumnWidth = (int)((dimension.width - (NAME_COL_WIDTH * 2f) - indexColumnWidth) / table.getAttendanceDates().size());
        }

        g.setColor(Color.BLACK);
        g.setFont(tableFont);

        int cy = cellHeight;

        final AffineTransform oTrans = g.getTransform();
        g.translate(0, currScroll);
        for (int r = 0; r < table.getStudentCount(); r++) {
            drawRow(g, cy, 0, r);
            cy += cellHeight;
        }
        g.setTransform(oTrans);

        // Draw header row
        int xPos = 0;
        drawCell(g, xPos, 0, indexColumnWidth, cellHeight, Color.LIGHT_GRAY, "ID"); xPos += indexColumnWidth;
        drawCell(g, xPos, 0, NAME_COL_WIDTH, cellHeight, Color.LIGHT_GRAY, "Last"); xPos += NAME_COL_WIDTH;
        drawCell(g, xPos, 0, NAME_COL_WIDTH, cellHeight, Color.LIGHT_GRAY, "First"); xPos += NAME_COL_WIDTH;
        for(LocalDate date : table.getAttendanceDates()) {
            drawCell(g, xPos, 0, attendanceColumnWidth, cellHeight, Color.LIGHT_GRAY, DATE_FORMATTER.format(date));
            xPos += attendanceColumnWidth;
        }
    }

    private void drawRow(Graphics g, int cy, int cx, int r) {
        final Student student = table.getStudent(r);

        // Start with the default background
        Color cellColor = r % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE;
        if(student == highlightStudent) {
            cellColor = Color.YELLOW;
        }

        // Draw column headers ( ID, First/ Last Name )
        drawCell(g, cx, cy, indexColumnWidth, cellHeight, cellColor, Integer.toString(r + 1));
        cx += indexColumnWidth;

        drawCell(g, cx, cy, NAME_COL_WIDTH, cellHeight, cellColor, student.getLastName());
        cx += NAME_COL_WIDTH;

        drawCell(g, cx, cy, NAME_COL_WIDTH, cellHeight, cellColor, student.getFirstName());
        cx += NAME_COL_WIDTH;

        boolean foundCurrentDate = false;

        // Draw attendance columns
        final List<LocalDate> attendanceDates = table.getAttendanceDates();
        for (int i = 0; i < attendanceDates.size(); i++) {
            final LocalDate date = attendanceDates.get(i);
            final boolean isCurrentDateColumn = Objects.equals(date, Settings.getInstance().getDate());
            cellColor = r % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE;
            if (isCurrentDateColumn) {
                foundCurrentDate = true;
                if (student.isSignedIn(date)) {
                    if (Settings.getInstance().getLoginType() == LoginType.IN_ONLY ||
                            student.isSignedOut(date)) {
                        cellColor = Color.GREEN;
                    } else {
                        cellColor = Color.ORANGE;
                    }
                } else {
                    cellColor = CURRENT_DATE_COL;
                }
            } else if (!foundCurrentDate) {
                if (student.isSignedIn(date)) {
                    cellColor = r % 2 == 0 ? PRESENT_EVEN : PRESENT_ODD;
                } else {
                    cellColor = r % 2 == 0 ? ABSENT_EVEN : ABSENT_ODD;
                }
            } else {
                g.setColor(Color.GRAY);
            }

            // Handle Highlighting now.
            if (student == highlightStudent) {
                int localMax = highlightTimerMax / 2;
                if (isCurrentDateColumn) {
                    int timer = highlightTimerMax - highlightTimer - highlightTimerMax / 4;
                    if (timer < 0) timer = 0;
                    if (timer > localMax) timer = localMax;

                    float th = (timer) / (float) localMax * (float) Math.PI * 2f;
                    float a = (float) (-Math.cos(th) + 1) / 2f;

                    cellColor = new Color(0f, 1f, 0f, a);
                } else if (!foundCurrentDate) {
                    float thruBar = i / attendanceDates.size();

                    int timer = highlightTimerMax - highlightTimer - (int) (thruBar * highlightTimerMax / 4);
                    if (timer < 0) timer = 0;
                    if (timer > localMax) timer = localMax;

                    float th = (timer) / (float) localMax * (float) Math.PI * 2f;
                    float a = (float) (-Math.cos(th) + 1) / 2f;

                    cellColor = new Color(0f, 1f, 0f, a / 2f);
                }
            }

            String cellText = null;
            if (Settings.getInstance().getLoginType() == LoginType.IN_OUT &&
                    foundCurrentDate) {
                if(student.isSignedOut(date)) {
                    cellText = "Out: " + TIME_FORMATTER.format(student.getSignOutTime(date));
                } else  if(student.isSignedIn(date)) {
                    cellText = "In: " + TIME_FORMATTER.format(student.getSignInTime(date));
                }
            }

            drawCell(g, cx, cy, attendanceColumnWidth, cellHeight, cellColor, cellText);
            cx += attendanceColumnWidth;
        }

        // Draw column footer (Totals)
    }

    private void drawCell(Graphics g, int left, int top, int width, int height, Color background, String text) {
        g.setColor(background);
        g.fillRect(left, top, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(left, top, width, height);

        if(!BarcodeUtils.isNullOrEmpty(text)) {
            g.drawString(text, left + 4, top + height/2 + g.getFont().getSize()/2);
        }
    }

    private void highlightStudent(Student student) {
        highlightStudent = student;
        highlightTimer = highlightTimerMax;
        scrollTimer = 0;
        scrollAcc = 0;
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

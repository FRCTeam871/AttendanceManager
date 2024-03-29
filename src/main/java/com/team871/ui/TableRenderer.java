package com.team871.ui;

import com.team871.data.FirstRegistration;
import com.team871.data.Member;
import com.team871.data.SafeteyFormState;
import com.team871.util.Settings;
import com.team871.util.Utils;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class TableRenderer implements MouseWheelListener, MouseListener {
    private static final DecimalFormat DURATION_FORMAT = new DecimalFormat("(##.##h)");
    private static final int NAME_COL_WIDTH = 100;

    private static final Color CURRENT_DATE_COL = new Color(225, 210, 110);
    private static final Color ABSENT_EVEN = new Color(0.7f, 0.35f, 0.35f);
    private static final Color ABSENT_ODD = new Color(1f, 0.4f, 0.4f);
    private static final Color PRESENT_EVEN = new Color(0.3f, 0.65f, 0.3f);
    private static final Color PRESENT_ODD = new Color(0.4f, 1f, 0.4f);

    private final AttendanceTable table;

    private Font tableFont;

    private float destScroll = 0f;
    private float currScroll = 0f;
    private int cellHeight = 25;
    private int indexColumnWidth = 0;
    private int attendanceColumnWidth = 0;

    private Member highlightMember;
    private int highlightTimer;
    private int highlightTimerMax = 120;

    private int scrollTimer = 0;
    private int scrollAcc = 0;

    private Rectangle dimension;
    private int maxScroll;

    private boolean allowAutoScroll = true;

    public TableRenderer(AttendanceTable table) {
        this.table = table;
        tableFont = new Font("Arial", Font.BOLD, 12);

        table.addListener(new AttendanceTable.Listener() {
            @Override
            public void onSignIn(Member member) {
                highlightStudent(member);
            }

            @Override
            public void onSignOut(Member member) {
                highlightStudent(member);
            }

            @Override
            public void nameChanged(Member member) {
                highlightStudent(member);
            }

            @Override
            public void onStudentAdded(Member member) {
                highlightStudent(member);
            }
        });
    }

    public void tick(int time) {
        if (highlightTimer == 0) {
            highlightMember = null;
            highlightTimer = -1;
        }

        float speed;

        if (scrollTimer > 0) {
            destScroll += scrollAcc * -cellHeight;
            destScroll = cellHeight * (Math.round(destScroll / cellHeight)) - 1;
            scrollAcc = 0;
            speed = 5f;
            scrollTimer--;
        } else {
            if (highlightMember != null) {
                destScroll = -(cellHeight * (table.getStudentIndex(highlightMember) - 2));
                speed = 10f;
            } else {
                destScroll = -(int) (((Math.sin(time / (60f * 4)) + 1) / 2f) * maxScroll);
                speed = 20f;
            }
        }

        if (destScroll > 0) {
            destScroll = 0;
        }

        if (-destScroll > maxScroll) {
            destScroll = -maxScroll;
        }

        if(allowAutoScroll) {
            currScroll += (destScroll - currScroll) / speed;
        }

        if (highlightTimer > 0) {
            highlightTimer--;
        }
    }

    public void drawTable(Graphics2D g) {
        if (indexColumnWidth <= 0) {
            indexColumnWidth = g.getFontMetrics().stringWidth(Integer.toString(table.getStudentCount())) + 10;
        }

        attendanceColumnWidth = (int) ((dimension.width - (NAME_COL_WIDTH * 2f) - indexColumnWidth) / table.getAttendanceDates().size());

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
        drawCell(g, xPos, 0, indexColumnWidth, cellHeight, Color.LIGHT_GRAY, Utils.ID_COL);
        xPos += indexColumnWidth;
        drawCell(g, xPos, 0, NAME_COL_WIDTH, cellHeight, Color.LIGHT_GRAY, Utils.LAST_NAME_COL);
        xPos += NAME_COL_WIDTH;
        drawCell(g, xPos, 0, NAME_COL_WIDTH, cellHeight, Color.LIGHT_GRAY, Utils.FIRST_NAME_COL);
        xPos += NAME_COL_WIDTH;
        for (LocalDate date : table.getAttendanceDates()) {
            drawCell(g, xPos, 0, attendanceColumnWidth, cellHeight, Color.LIGHT_GRAY, Utils.DATE_FORMATTER.format(date));
            xPos += attendanceColumnWidth;
        }
    }

    private void drawRow(Graphics g, int cy, int cx, int r) {
        final Member member = table.getStudent(r);

        // Start with the default background
        Color cellColor;

        // Draw column headers ( ID, First/ Last Name )
        drawCell(g, cx, cy, indexColumnWidth / 2, cellHeight, getSafetyFormColor(member), null);
        drawCell(g, cx + (indexColumnWidth / 2), cy, indexColumnWidth / 2, cellHeight, getRegistrationColor(member), null);
        cellColor = new Color(0, 0, 0, 0);
        drawCell(g, cx, cy, indexColumnWidth, cellHeight, cellColor, Integer.toString(r + 1));
        cx += indexColumnWidth;

        drawCell(g, cx, cy, NAME_COL_WIDTH, cellHeight, cellColor, member.getLastName());
        cx += NAME_COL_WIDTH;

        drawCell(g, cx, cy, NAME_COL_WIDTH, cellHeight, cellColor, member.getFirstName());
        // Draw in cute little hour counts
        final Font oldFont = g.getFont();
        g.setFont(g.getFont().deriveFont(10.0f));
        final String text = DURATION_FORMAT.format(member.getTotalHours());
        g.drawString(text, cx + NAME_COL_WIDTH - 4 - g.getFontMetrics().stringWidth(text),
                cy + g.getFont().getSize() + 4);
        g.setFont(oldFont);
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
                if (member.isSignedIn(date)) {
                    if (Settings.getInstance().getLoginType() == LoginType.IN_ONLY ||
                            member.isSignedOut(date)) {
                        cellColor = Color.GREEN;
                    } else {
                        cellColor = Color.ORANGE;
                    }
                } else {
                    cellColor = CURRENT_DATE_COL;
                }
            } else if (!foundCurrentDate) {
                if (member.isSignedIn(date)) {
                    cellColor = r % 2 == 0 ? PRESENT_EVEN : PRESENT_ODD;
                } else {
                    cellColor = r % 2 == 0 ? ABSENT_EVEN : ABSENT_ODD;
                }
            } else {
                g.setColor(Color.GRAY);
            }

            String cellText = null;
            if (Settings.getInstance().getLoginType() == LoginType.IN_OUT &&
                    foundCurrentDate) {
                if (member.isSignedOut(date)) {
                    cellText = "Out: " + Utils.TIME_FORMATTER.format(member.getSignOutTime(date));
                } else if (member.isSignedIn(date)) {
                    cellText = "In: " + Utils.TIME_FORMATTER.format(member.getSignInTime(date));
                }
            }

            drawCell(g, cx, cy, attendanceColumnWidth, cellHeight, cellColor, cellText);

            // Handle Highlighting now.
            if (member == highlightMember) {
                int localMax = highlightTimerMax / 2;
                if (isCurrentDateColumn) {
                    int timer = highlightTimerMax - highlightTimer - highlightTimerMax / 4;
                    if (timer < 0) timer = 0;
                    if (timer > localMax) timer = localMax;

                    float th = (timer) / (float) localMax * (float) Math.PI * 2f;
                    float a = (float) (-Math.cos(th) + 1) / 2f;

                    cellColor = new Color(0f, 1f, 0f, a);
                    drawCell(g, cx, cy, attendanceColumnWidth, cellHeight, cellColor, cellText);
                } else if (!foundCurrentDate) {
                    float thruBar = (float) i / attendanceDates.size();

                    int timer = highlightTimerMax - highlightTimer - (int) (thruBar * highlightTimerMax / 4);
                    if (timer < 0) timer = 0;
                    if (timer > localMax) timer = localMax;

                    float th = (timer) / (float) localMax * (float) Math.PI * 2f;
                    float a = (float) (-Math.cos(th) + 1) / 2f;

                    cellColor = new Color(0f, 1f, 0f, a / 2f);
                    drawCell(g, cx, cy, attendanceColumnWidth, cellHeight, cellColor, cellText);
                }
            }
            cx += attendanceColumnWidth;
        }

        // Draw column footer (Totals)
    }

    private Color getSafetyFormColor(Member member) {
        final SafeteyFormState state = member.getSafeteyFormState();
        if (state == null) {
            return Color.GRAY;
        }

        switch (state) {
            default:
            case None:
                return Color.GRAY;
            case Printed:
                return Color.CYAN;
            case Given:
                return Color.RED;
            case Signed:
                return Color.GREEN;
        }
    }

    private Color getRegistrationColor(Member member) {
        final FirstRegistration registration = member.getRegistration();
        if (registration == null) {
            return Color.GRAY;
        }

        switch (registration) {
            default:
                return Color.GRAY;
            case None:
                return Color.RED;
            case MissingWaiver:
                return Color.ORANGE;
            case Complete:
                return Color.GREEN;
        }
    }

    private void drawCell(Graphics g, int left, int top, int width, int height, Color background, String text) {
        g.setColor(background);
        g.fillRect(left, top, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(left, top, width, height);

        if (!Utils.isNullOrEmpty(text)) {
            g.drawString(text, left + 4, top + height / 2 + g.getFont().getSize() / 2);
        }
    }

    private void highlightStudent(Member member) {
        highlightMember = member;
        highlightTimer = highlightTimerMax;
        scrollTimer = 0;
        scrollAcc = 0;
    }

    public void setDimension(Rectangle dimension) {
        this.dimension = dimension;
        this.maxScroll = Math.max(0, (cellHeight * (table.getStudentCount() + 1)) - dimension.height + 2);
    }

    public void setAutoScroll(boolean isAutoScroll) {
        this.allowAutoScroll = isAutoScroll;
    }

    private Member getMemberAt(Point p) {
        final double hPos = p.getY() - currScroll - dimension.y;
        long index = (long) (Math.floor(hPos / cellHeight) - 1);

        if(index < table.getStudentCount() && index >= 0) {
            return table.getStudent((int) index);
        }

        return null;
    }

    //region listeners
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        scrollAcc = e.getWheelRotation();
        scrollTimer = 120;
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if(SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
            setAutoScroll(false);
            final Member memberUnderCursor = getMemberAt(e.getPoint());
            if(memberUnderCursor == null) {
                return;
            }

            final JPopupMenu menu = new JPopupMenu();

            final JMenu firstRegMenu = new JMenu("First Registration");
            menu.add(new JLabel(memberUnderCursor.getFirstName() + " " + memberUnderCursor.getLastName()));
            menu.add(new JSeparator());

            final ButtonGroup regButtonGroup = new ButtonGroup();
            JRadioButtonMenuItem radioItem = new JRadioButtonMenuItem(FirstRegistration.None.toString(), memberUnderCursor.getRegistration() == FirstRegistration.None);
            radioItem.addItemListener(e1 -> memberUnderCursor.setFirstRegistration(FirstRegistration.None));
            regButtonGroup.add(radioItem);
            firstRegMenu.add(radioItem);

            radioItem = new JRadioButtonMenuItem(FirstRegistration.MissingWaiver.toString(), memberUnderCursor.getRegistration() == FirstRegistration.MissingWaiver);
            radioItem.addItemListener(e1 -> memberUnderCursor.setFirstRegistration(FirstRegistration.MissingWaiver));
            regButtonGroup.add(radioItem);
            firstRegMenu.add(radioItem);

            radioItem = new JRadioButtonMenuItem(FirstRegistration.Complete.toString(), memberUnderCursor.getRegistration() == FirstRegistration.Complete);
            radioItem.addItemListener(e1 -> memberUnderCursor.setFirstRegistration(FirstRegistration.Complete));
            regButtonGroup.add(radioItem);
            firstRegMenu.add(radioItem);
            menu.add(firstRegMenu);

            JMenu safetyMenu = new JMenu("Safety Form");
            final ButtonGroup safetyButtonGroup = new ButtonGroup();
            radioItem = new JRadioButtonMenuItem(SafeteyFormState.None.toString(), memberUnderCursor.getSafeteyFormState() == SafeteyFormState.None);
            radioItem.addItemListener(e1 -> memberUnderCursor.setSafetyState(SafeteyFormState.None));
            safetyButtonGroup.add(radioItem);
            safetyMenu.add(radioItem);

            radioItem = new JRadioButtonMenuItem(SafeteyFormState.Printed.toString(), memberUnderCursor.getSafeteyFormState() == SafeteyFormState.Printed);
            radioItem.addItemListener(e1 -> memberUnderCursor.setSafetyState(SafeteyFormState.Printed));
            safetyButtonGroup.add(radioItem);
            safetyMenu.add(radioItem);

            radioItem = new JRadioButtonMenuItem(SafeteyFormState.Given.toString(), memberUnderCursor.getSafeteyFormState() == SafeteyFormState.Given);
            radioItem.addItemListener(e1 -> memberUnderCursor.setSafetyState(SafeteyFormState.Given));
            safetyButtonGroup.add(radioItem);
            safetyMenu.add(radioItem);

            radioItem = new JRadioButtonMenuItem(SafeteyFormState.Signed.toString(), memberUnderCursor.getSafeteyFormState() == SafeteyFormState.Signed);
            radioItem.addItemListener(e1 -> memberUnderCursor.setSafetyState(SafeteyFormState.Signed));
            safetyButtonGroup.add(radioItem);
            safetyMenu.add(radioItem);
            menu.add(safetyMenu);

            JMenuItem regItem = new JMenuItem(new AbstractAction("Edit Student") {
                @Override
                public void actionPerformed(ActionEvent dontcare) {
                    final StudentEditor editor = new StudentEditor(memberUnderCursor);
                    editor.setLocationRelativeTo(e.getComponent());
                    editor.setVisible(true);
                }
            });
            menu.add(regItem);

            menu.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {

                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    setAutoScroll(true);
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {
                    setAutoScroll(true);
                }
            });
            menu.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }
    //endregion
}

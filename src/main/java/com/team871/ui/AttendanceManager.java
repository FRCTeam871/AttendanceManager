package com.team871.ui;

import com.team871.data.Member;
import com.team871.exception.RobotechException;
import com.team871.sensing.AbstractBarcodeReader;
import com.team871.sensing.BarcodeResult;
import com.team871.sensing.JPOSSense;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Dave
 * i know this code is terrible
 * once i get a prototype working ill make it good
 */
public class AttendanceManager {
    private static final Logger logger = LoggerFactory.getLogger(AttendanceManager.class);
    private static final int TARGET_FRAMERATE = 60;
    private static final long MILLIS_PER_FRAME = 1000 / TARGET_FRAMERATE;

    private final Frame frame;
    private int time = 0;

    AbstractBarcodeReader barcodeSensor;

    private int flashTimer = 0;
    private int flashTimerMax = 30;

    private BarcodeResult lastResult;
    private String lastSID = "???";
    private Member member = null;

    TableRenderer tableRenderer;
    AttendanceTable table;

    TableRenderer mentorRenderer;
    AttendanceTable mentorTable;

    DisplayTable displayTable = DisplayTable.Students;

    private boolean enteringNewSID = false;

    private SettingsMenu settingsMenu;

    private int clearTimerMax = 60 * 4;
    private int clearTimer = clearTimerMax;

    private State currentState = State.Normal;
    private Workbook workbook;

    private enum State {
        Settings,
        Normal,
        Shutdown
    }

    private enum DisplayTable {
        Students, Mentors
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                Settings.getInstance().init(args[0]);
            } else {
                throw new RobotechException("No prefs file provided");
            }

            final AttendanceManager manager = new AttendanceManager();
            manager.init();
            manager.run();
        } catch (RobotechException e) {
            logger.error("Failed to initialize: ", e);
        }
    }

    private AttendanceManager() {
        logger.info("Initializing AttendenceManager");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            logger.error("Error setting LAF: " + e.toString());
        }
        frame = new Frame();
    }

    private void init() throws RobotechException {
        barcodeSensor = new JPOSSense();
        settingsMenu = new SettingsMenu(this, barcodeSensor);
        barcodeSensor.addListener( (code, changed) -> {
            if (BarcodeUtils.isSettingsCommand(code)) {
                settingsMenu.handleResult(code);
            } else if(BarcodeUtils.isAdminCommand(code)) {
                handleAdmin(code);
            } else {
                handleBarcode(code);
            }
        });
        final Settings settings = Settings.getInstance();

        try(final FileInputStream stream = new FileInputStream(settings.getSheetPath().toFile())) {
            workbook = WorkbookFactory.create(stream);
        } catch (IOException e) {
            throw new RobotechException("Failed to load attendance file", e);
        }

        table = new AttendanceTable(workbook,
                                settings.getRosterSheet(), settings.getAttendanceSheet(),
                                settings.getAttendanceHeaderRow(), settings.getRosterHeaderRow(),
                                settings.getAttendanceFirstDataRow(), settings.getRosterFirstDataRow());
        tableRenderer = new TableRenderer(table);

        mentorTable = new AttendanceTable(workbook,
                settings.getMentorRosterSheet(), settings.getMentorAttendanceSheet(),
                settings.getMentorAttendanceHeaderRow(), settings.getMentorRosterHeaderRow(),
                settings.getMentorAttendanceFirstDataRow(), settings.getMentorRosterFirstDataRow());

        mentorRenderer = new TableRenderer(mentorTable);

        frame.addMouseWheelListener(tableRenderer);
        frame.addMouseWheelListener(mentorRenderer);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_S && e.isControlDown()) {
                    showSaveDialog();
                } else if(e.getKeyCode() == KeyEvent.VK_Q && e.isControlDown()) {
                    if (table.hasUnsaved() || mentorTable.hasUnsaved()) {
                        int result = JOptionPane.showConfirmDialog(frame.getCanvas(), "You have unsaved changes!\nDo you want to save?", frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                showSaveDialog();
                                int result2 = JOptionPane.showConfirmDialog(frame.getCanvas(), "Exit?", frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                                if (result2 == JOptionPane.YES_OPTION) {
                                    currentState = State.Shutdown;
                                }
                                break;
                            case JOptionPane.NO_OPTION:
                                currentState = State.Shutdown;
                                break;
                            default: // cancel or x-out
                                // don't do anything
                                break;
                        }
                    } else {
                        currentState = State.Shutdown;
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_F5) {
                    currentState = currentState == State.Settings ? State.Normal : State.Settings;
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown() && e.isAltDown()) {
                    playYeaTim();
                } else if(e.getKeyCode() == KeyEvent.VK_L) {
                    settingsMenu.doManualLogin();
                } else if(e.getKeyCode() == KeyEvent.VK_N) {
                    switch (displayTable) {
                        case Students:
                            displayTable = DisplayTable.Mentors;
                            break;
                        case Mentors:
                            displayTable = DisplayTable.Students;
                            break;
                    }
                }
            }
        });

        frame.setVisible(true);
    }

    private void run() {
        long timer = System.currentTimeMillis();
        long preRenderTime;

        int frames = 0;
        int ticks = 0;

        long totalSleepTime = 0;
        long totalTickTime = 0;
        long totalRenderTime = 0;

        long last = System.currentTimeMillis();
        while (currentState != State.Shutdown) {
            long now = System.currentTimeMillis();
            long diff = now - last;

            for (long tickDelta = diff / MILLIS_PER_FRAME; tickDelta >= 1; tickDelta--) {
                tick();
                ticks++;
            }

            preRenderTime = System.currentTimeMillis();
            totalTickTime += (preRenderTime - now);

            render();
            totalRenderTime += System.currentTimeMillis() - preRenderTime;
            frames++;
            last = now;

            try {
                final long sleepTime = Math.max(1, MILLIS_PER_FRAME - (System.currentTimeMillis() - now));
                totalSleepTime += sleepTime;
                Thread.sleep(sleepTime);
            } catch (InterruptedException ignored) {
            }

            if (System.currentTimeMillis() - timer >= 1000) {
                timer = System.currentTimeMillis();
                frame.setTitle("Attendance UI | " + frames + " FPS " + ticks + " TPS " + ((float) totalSleepTime / frames) + " " + ((float) totalTickTime / frames) + " " + (totalRenderTime / frames));
                totalSleepTime = 0;
                totalRenderTime = 0;
                totalTickTime = 0;
                frames = 0;
                ticks = 0;
            }
        }

        barcodeSensor.shutdown();
        System.exit(0);
    }

    private void tick() {
        if (flashTimer > 0) {
            flashTimer--;
        }

        barcodeSensor.tick(time);
        switch (displayTable) {
            case Students:
                tableRenderer.tick(time);
                break;
            case Mentors:
                mentorRenderer.tick(time);
                break;
        }
        settingsMenu.tick(time);

        if (clearTimer > 0) {
            clearTimer--;
            if (clearTimer == 0) {
                clearTimer = -1;
                lastResult = null;
                member = null;
                lastSID = "???";
                barcodeSensor.resetLast();
            }
        }

        time++;
    }

    private void render() {
        final Graphics2D g = frame.getCanvas().getRenderGraphics();
        final Canvas canvas = frame.getCanvas();
        switch (currentState) {
            case Settings:
                settingsMenu.render(g, canvas.getWidth(), canvas.getHeight());
                break;
            case Normal:
                renderNormal(g, canvas.getWidth(), canvas.getHeight());
                break;
        }

        frame.paint();
    }

    private void renderBackground(Graphics2D g, int width, int height) {
        if (frame.hasFocus()) {
            g.setColor(Color.RED);
        } else if (flashTimer > 0) {
            Color c1 = Color.DARK_GRAY;
            Color c2 = Color.GREEN;
            float thru = flashTimer / (float) flashTimerMax;
            int r = (int) (c1.getRed() + thru * (c2.getRed() - c1.getRed()));
            int gr = (int) (c1.getGreen() + thru * (c2.getGreen() - c1.getGreen()));
            int b = (int) (c1.getBlue() + thru * (c2.getBlue() - c1.getBlue()));
            Color lerp = new Color(r, gr, b);
            g.setColor(lerp);
        } else {
            g.setColor(Color.DARK_GRAY);
        }

        g.fillRect(0, 0, width, height);
    }

    private void renderSensorPanel(Graphics2D g, Rectangle sensorPanel) {
        // "Camera" rectangle.  In reality, this is where the scanner will render state.
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(sensorPanel.x, sensorPanel.y, sensorPanel.width, sensorPanel.height);

        g.setColor(Color.BLUE);
        g.drawRect(100 + (int) (50 * Math.sin(time / 10f)), 100 + (int) (50 * Math.cos(time / 10f)), 20, 20);

        AffineTransform tr = g.getTransform();
        g.setClip(sensorPanel);
        g.translate(sensorPanel.x, sensorPanel.y);
        barcodeSensor.render(g, sensorPanel.width, sensorPanel.height);
        g.setTransform(tr);
        g.setClip(null);
    }

    private void renderInfoRectangle(Graphics2D g, Rectangle infoRect) {
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(infoRect.x, infoRect.y, infoRect.width, infoRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(infoRect.x, infoRect.y, infoRect.width, infoRect.height);

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 32));

        final List<String> lines = new ArrayList<>();
        lines.add("Scanned: " + (lastResult == null ? "???" : lastResult.getText()));
        lines.add("SID (hash): " + lastSID);
        if (!lastSID.equalsIgnoreCase("???") && member != null) {
            lines.add("Name: " + member.getLastName());
        }

        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), infoRect.x + 10, infoRect.y + 32 + 32 * i);
        }
    }

    private void renderTable(Graphics2D g, Rectangle tableRect) {
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);

        g.setStroke(new BasicStroke(1f));

        final AffineTransform tr = g.getTransform();
        g.setClip(tableRect);
        g.translate(tableRect.x, tableRect.y);

        switch (displayTable) {
            case Students:
                tableRenderer.setDimension(tableRect);
                tableRenderer.drawTable(g);
                break;
            case Mentors:
                mentorRenderer.setDimension(tableRect);
                mentorRenderer.drawTable(g);
                break;
        }

        g.setTransform(tr);
    }

    private void renderNormal(Graphics2D g, int width, int height) {
        int padding = 16;

        renderBackground(g, width, height);
        renderSensorPanel(g, new Rectangle(padding, padding, (int) (width * 0.4), (int) (height * 0.4)));
        renderInfoRectangle(g, new Rectangle((int) (width - width * 0.5 - padding), padding, (int) (width * 0.5), (int) (height * 0.4)));
        renderTable(g, new Rectangle(padding, (int) (height * 0.4 + padding + padding), width - padding * 2, (int) ((height) - (height * 0.4 + padding + padding) - padding)));
    }

    private void handleBarcode(BarcodeResult result) {
        if (enteringNewSID ||
            currentState == State.Settings ||
            BarcodeUtils.isSettingsCommand(result) ||
            BarcodeUtils.isAdminCommand(result)) {
            return; //don't accept new scans if setting up new SID
        }

        logger.info("Scanned: " + result.getText());
        clearTimer = clearTimerMax;
        this.member = null;
        final String newSID = result.getText().replaceFirst("^0+(?!$)", ""); // remove leading zeros;

        if(!isValidSID(newSID)) {
            lastResult = result;
            lastSID = "INVALID";
            if (lastResult.getText().equals("871")) {
                lastSID = "YEA TIM";
                this.member = null;
                playYeaTim();
            }
            return;
        }

        lastResult = new BarcodeResult("(hidden student id)", result.getTime());
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(newSID.getBytes());
            lastSID = Integer.toHexString(Arrays.hashCode(messageDigest.digest()));
            logger.info("hash = " + lastSID);
        } catch (Exception e) {
            logger.error("Error validating MD5sum", e);
        }

        final Member member = findMember(newSID);
        if (member != null) {
            handleLogin(member);
        } else if (!enteringNewSID) {
            enteringNewSID = true;
            SwingUtilities.invokeLater(() -> handleNewSID(newSID));
        }
    }

    private Member findMember(String newSID) {
        Member member = table.getStudentById(newSID);
        if(member == null) {
            member = mentorTable.getStudentById(newSID);
        }

        return member;
    }

    private void handleNewSID(String sid) {
        int response = JOptionPane.showConfirmDialog(frame.getCanvas(), "The scanned ID is not present in the system.\nAdd it?", frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        cancel:
        if (response == JOptionPane.YES_OPTION) {
            Map<String, Member> students;
            String name = null;
            do {
                name = JOptionPane.showInputDialog((name != null ? "That name is not present.\n" : "") + "Enter the last name for the member associated with this ID:");
                if (name == null) {
                    break cancel;
                }
            } while ((students = getStudentsWithLastName(name)).isEmpty());

            Member member;
            if (students.size() > 1) {
                String firstName = null;
                do {
                    firstName = JOptionPane.showInputDialog((firstName != null ? "That name is not present.\n" : "") + "There are multiple people with that last name!\nEnter the first name for the member associated with this ID:");
                    if (firstName == null) {
                        break cancel;
                    }
                } while ((member = students.get(firstName)) == null);
            } else {
                member = students.values().iterator().next();
            }

            member.setId(sid);
            handleLogin(member);
        }

        enteringNewSID = false;
    }

    Map<String, Member> getStudentsWithLastName(String name) {
        Map<String, Member> members = table.getStudentsWithLastName(name);

        if(members == null || members.isEmpty()) {
            members = mentorTable.getStudentsWithLastName(name);
        }

        return members;
    }

    private void handleLogin(Member member) {
        if (barcodeSensor instanceof JPOSSense) {
            ((JPOSSense) barcodeSensor).dance();
        }

        final LocalDate today = Settings.getInstance().getDate();
        if (!member.isSignedIn(today)) {
            member.signIn(today);
            flashTimer = flashTimerMax;
        } else if (member.isSignedIn(today)) {
            member.signOut(today);
            flashTimer = flashTimerMax;
        }

        this.member = member;
    }

    private void handleAdmin(BarcodeResult result) {
        final String cmd = BarcodeUtils.getAdminCommand(result);
        if ("SETTINGS".equals(cmd)) {
            currentState = currentState == State.Settings ? State.Normal : State.Settings;
        }
    }

    private boolean isValidSID(String test) {
        if (!test.matches("^F?\\d+(\\d+)?$")) {
            return false; //is numeric
        }

        return test.length() >= 5 && test.length() <= 7; // must be 5 or 6 digits
    }

    private void showSaveDialog() {
        if(!table.areAllSignedOut() || !mentorTable.areAllSignedOut()) {
            int result = JOptionPane.showConfirmDialog(null, "There are people that haven't signed out.\nDo you want to sign them out?\n(If not, sign in time will be saved)", "Attendance Manager", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                table.forceSignOut();
                mentorTable.forceSignOut();
            }
        }

        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(Settings.getInstance().getSheetPath().toFile());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.showOpenDialog(frame.getCanvas());

        final File f = chooser.getSelectedFile();

        boolean success = true;
        System.out.println("Saving attendance to " + f.getAbsolutePath());
        try {
            f.createNewFile(); //create the file if it doesn't exist
            FileOutputStream out = new FileOutputStream(f);
            workbook.write(out); // write the workbook to the file
            out.close();
        } catch(Exception e) {
            logger.warn("Error writing spreadsheet: ", e);
            success = false;
        }

        table.setSaved();
        mentorTable.setSaved();

        if (success) {
            JOptionPane.showMessageDialog(frame.getCanvas(), "Attendance saved.", frame.getTitle(), JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame.getCanvas(), "Failed to save attendance (see console).", frame.getTitle(), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void playYeaTim() {
        try {
            final Clip yeatim = AudioSystem.getClip();

            final AudioInputStream inputStream = AudioSystem.getAudioInputStream(SettingsMenu.class.getClassLoader().getResource("audio/tim.wav"));
            yeatim.open(inputStream);
            yeatim.setFramePosition(0);
            yeatim.start();
        } catch (Exception e) {
            logger.error("Failed to YeahTim", e);
        }
    }

    public void setFullscreen(boolean fullscreen) {
        frame.setFullscreen(fullscreen);
    }

    public boolean isFullscreen() {
        return frame.isFullscreen();
    }

    public Canvas getCanvas() {
        return frame.getCanvas();
    }

    //// incubation

    public void createStudent(String first, String last) {
        switch (displayTable) {
            case Students:
                table.createStudent(first, last);
                break;
            case Mentors:
                mentorTable.createStudent(first, last);
                break;
        }
    }
}

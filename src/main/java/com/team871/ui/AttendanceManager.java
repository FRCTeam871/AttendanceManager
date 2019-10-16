package com.team871.ui;

import com.team871.sensing.BarcodeResult;
import com.team871.sensing.AbstractBarcodeReader;
import com.team871.sensing.JPOSSense;
import com.team871.sensing.ResultListener;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dave
 * i know this code is terrible
 * once i get a prototype working ill make it good
 */
public class AttendanceManager implements ResultListener {
    private static final Logger logger = LoggerFactory.getLogger(AttendanceManager.class);
    private static final int TARGET_FRAMERATE = 60;
    private static final long MILLIS_PER_FRAME = 1000 / TARGET_FRAMERATE;

    Frame frame;
    private int time = 0;

    AbstractBarcodeReader barcodeSensor;

    private int flashTimer = 0;
    private int flashTimerMax = 30;

    private BarcodeResult lastResult;
    private String lastSID = "???";
    private String lastName = "";

    SheetWrapper sheetWrapper;
    private boolean enteringNewSID = false;

    private SettingsMenu settings;

    private int clearTimerMax = 60 * 4;
    private int clearTimer = clearTimerMax;

    private State currentState = State.Normal;

    private enum State {
        Settings,
        Normal,
        Shutdown
    }

    public static void main(String[] args) {
        Settings.init();
        if (args.length > 0) {
            Settings.setPrefsFile(new File(args[0]));
        } else {
            Settings.setPrefsFile(Settings.getDefaultPrefsFile());
        }

        final AttendanceManager manager = new AttendanceManager();
        manager.init();
        manager.run();
    }

    private AttendanceManager() {
        logger.info("Initializing AttendenceManager");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            logger.error("Error setting LAF: " + e.toString());
        }
    }

    private void init() {
        barcodeSensor = new JPOSSense();

        frame = new Frame();
        settings = new SettingsMenu(this, barcodeSensor);
        barcodeSensor.addListener(this);

        sheetWrapper = new SheetWrapper(Settings.getSheetPath());

        frame.addMouseWheelListener(sheetWrapper);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_S && e.isControlDown()) {
                    int result = JOptionPane.showConfirmDialog(frame.getCanvas(), "Do you want to save?", frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

                    if (result == JOptionPane.YES_OPTION) {
                        showSaveDialog();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_F5) {
                    currentState = currentState == State.Settings ? State.Normal : State.Settings;
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown() && e.isAltDown()) {
                    playYeaTim();
                }
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (sheetWrapper.hasUnsaved()) {
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
    }

    private void tick() {
        if (flashTimer > 0) {
            flashTimer--;
        }

        barcodeSensor.update();
        sheetWrapper.tick(time);
        settings.tick();

        if (clearTimer > 0) {
            clearTimer--;
            if (clearTimer == 0) {
                clearTimer = -1;
                lastResult = null;
                lastName = "";
                lastSID = "???";
                barcodeSensor.resetCache();
            }
        }

        time++;
    }

    private void render() {
        final Graphics2D g = frame.getCanvas().getRenderGraphics();
        final Canvas canvas = frame.getCanvas();
        switch (currentState) {
            case Settings:
                settings.render(g, canvas.getWidth(), canvas.getHeight());
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

    private void renderCameraRectangle(Graphics2D g, Rectangle camRect) {
        // "Camera" rectangle.  In reality, this is where the scanner will render state.
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(camRect.x, camRect.y, camRect.width, camRect.height);

        g.setColor(Color.BLUE);
        g.drawRect(100 + (int) (50 * Math.sin(time / 10f)), 100 + (int) (50 * Math.cos(time / 10f)), 20, 20);

        AffineTransform tr = g.getTransform();
        g.setClip(camRect);
        g.translate(camRect.x, camRect.y);
        barcodeSensor.renderPreview(g, camRect.width, camRect.height);
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
        if (!lastSID.equalsIgnoreCase("???")) {
            lines.add("Name: " + lastName);
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

        sheetWrapper.setDimension(tableRect);
        sheetWrapper.drawTable(g);

        g.setTransform(tr);
    }

    private void renderNormal(Graphics2D g, int width, int height) {
        int padding = 16;

        renderBackground(g, width, height);
        renderCameraRectangle(g, new Rectangle(padding, padding, (int) (width * 0.4), (int) (height * 0.4)));
        renderInfoRectangle(g, new Rectangle((int) (width - width * 0.5 - padding), padding, (int) (width * 0.5), (int) (height * 0.4)));
        renderTable(g, new Rectangle(padding, (int) (height * 0.4 + padding + padding), width - padding * 2, (int) ((height) - (height * 0.4 + padding + padding) - padding)));
    }

    @Override
    public void changed(BarcodeResult result) {
        if (enteringNewSID ||
            currentState == State.Settings ||
            BarcodeUtils.isSettingsCommand(result) ||
            BarcodeUtils.isAdminCommand(result)) {
            return; //don't accept new scans if setting up new SID
        }

        logger.info("Scanned: " + result.getText());

        clearTimer = clearTimerMax;

        String osid = result.getText().replaceFirst("^0+(?!$)", ""); // remove leading zeros;
        String sid;

        if (isValidSID(osid)) {
            lastResult = new BarcodeResult("(hidden student id)", result.getTime());
        } else {
            lastResult = result;
        }

        lastName = "...";

        if (isValidSID(osid)) {
            String newSid = osid;
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update(newSid.getBytes());
                String encryptedString = new String(messageDigest.digest());
                encryptedString = newSid.hashCode() + "";
                logger.info("hash = " + encryptedString);
            } catch (Exception e) {
                logger.error("Error validating MD5sum", e);
            }

            sid = newSid;

            lastSID = sid;
            if (sheetWrapper.getRowBySID(sid) != null) {
                if (Settings.getMode() == Mode.IN_ONLY) {
                    if (!sheetWrapper.isPresent(sid)) {
                        sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                        if (barcodeSensor instanceof JPOSSense) {
                            ((JPOSSense) barcodeSensor).dance();
                        }

                        if (sheetWrapper.setPresent(sid, true)) {
                            flashTimer = flashTimerMax;
                        }

                    }
                } else if (Settings.getMode() == Mode.IN_OUT) {
                    sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                    if (barcodeSensor instanceof JPOSSense) {
                        ((JPOSSense) barcodeSensor).dance();
                    }
                    if (!sheetWrapper.isSignedIn(sid) && !sheetWrapper.isSignedOut(sid)) {
                        if (sheetWrapper.signInBySID(sid)) {
                            flashTimer = flashTimerMax;
                        }
                    } else if (sheetWrapper.isSignedIn(sid)) {
                        if (sheetWrapper.signOutBySID(sid)) {
                            flashTimer = flashTimerMax;
                        }
                    }

                }
                lastName = sheetWrapper.sidToName(sid);
            } else if (!enteringNewSID) {
                enteringNewSID = true;
                new Thread(() -> {
                    int response = JOptionPane.showConfirmDialog(frame.getCanvas(), "The scanned ID is not present in the system.\nAdd it?", frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    cancel:
                    if (response == JOptionPane.YES_OPTION) {
                        List<Row> rows;
                        String name = null;
                        do {
                            name = JOptionPane.showInputDialog((name != null ? "That name is not present.\n" : "") + "Enter the last name for the member associated with this ID:");
                            if (name == null) break cancel;
                        } while ((rows = sheetWrapper.getRowWithNoSIDByLastName(name)).isEmpty());

                        if (rows.size() > 1) {
                            Row row;
                            String firstName = null;
                            do {
                                firstName = JOptionPane.showInputDialog((firstName != null ? "That name is not present.\n" : "") + "There are multiple people with that last name!\nEnter the first name for the member associated with this ID:");
                                if (firstName == null) break cancel;
                            } while ((row = sheetWrapper.getRowWithNoSIDByFullName(firstName, name)) == null);

                            sheetWrapper.setSIDByFullName(firstName, name, sid);
                        } else {
                            sheetWrapper.setSIDByLastName(name, sid);
                        }

                        if (Settings.getMode() == Mode.IN_ONLY) {
                            if (!sheetWrapper.isPresent(sid)) {
                                sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                                if (barcodeSensor instanceof JPOSSense) {
                                    ((JPOSSense) barcodeSensor).dance();
                                }

                                if (sheetWrapper.setPresent(sid, true)) {
                                    flashTimer = flashTimerMax;
                                }

                            }
                        } else if (Settings.getMode() == Mode.IN_OUT) {
                            sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                            if (barcodeSensor instanceof JPOSSense) {
                                ((JPOSSense) barcodeSensor).dance();
                            }
                            if (!sheetWrapper.isSignedIn(sid) && !sheetWrapper.isSignedOut(sid)) {
                                if (sheetWrapper.signInBySID(sid)) {
                                    flashTimer = flashTimerMax;
                                }
                            } else if (sheetWrapper.isSignedIn(sid)) {
                                if (sheetWrapper.signOutBySID(sid)) {
                                    flashTimer = flashTimerMax;
                                }
                            }
                        }
                    }
                    lastName = sheetWrapper.sidToName(sid);
                    enteringNewSID = false;
                }).start();
            }
        } else {
            lastSID = "INVALID";
            if (lastResult.getText().equals("871")) {
                lastSID = "YEA";
                lastName = "TIM";
                playYeaTim();
            }
        }
    }

    @Override
    public void scanned(BarcodeResult result) {
        if (BarcodeUtils.isSettingsCommand(result)) {
            settings.handleResult(result);
        } else if(BarcodeUtils.isAdminCommand(result)) {
            handleAdmin(result);
        }
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
        sheetWrapper.showNotSignedOutDialog();

        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(sheetWrapper.getFile());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.showOpenDialog(frame.getCanvas());

        final File f = chooser.getSelectedFile();
        final boolean success = sheetWrapper.save(f);

        if (success) {
            JOptionPane.showMessageDialog(frame.getCanvas(), "Attendance saved.", frame.getTitle(), JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame.getCanvas(), "Failed to save attendance (see console).", frame.getTitle(), JOptionPane.WARNING_MESSAGE);
        }
    }

    BarcodeResult getLastResult() {
        return lastResult;
    }

    String getLastSID() {
        return lastSID;
    }

    String getLastName() {
        return lastName;
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

    public boolean isFullscreen() {
        return frame.isFullscreen();
    }
}
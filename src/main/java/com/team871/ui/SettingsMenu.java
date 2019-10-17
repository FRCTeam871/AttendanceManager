package com.team871.ui;

import com.team871.sensing.BarcodeResult;
import com.team871.sensing.AbstractBarcodeReader;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;
import net.sourceforge.barbecue.Barcode;
import org.apache.poi.ss.usermodel.Row;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettingsMenu implements TickListener {
    private Map<Barcode, Runnable> actions;
    private Clip testSound;
    private AttendanceManager attendanceManager;
    private AbstractBarcodeReader barcodeSensor;

    private boolean lock = false;

    public SettingsMenu(AttendanceManager attendanceManager, AbstractBarcodeReader barcodeSensor) {
        this.attendanceManager = attendanceManager;
        this.barcodeSensor = barcodeSensor;
        registerActions();
    }

    private void registerActions() {
        actions = new LinkedHashMap<>();
        actions.put(BarcodeUtils.getBarcodeByName("Test Scanner"), () -> {
            System.out.println("test 1!");
            playTestSound();
        });

        actions.put(BarcodeUtils.getBarcodeByName("Set Date"), () -> new Thread(() -> {
            lock = true;

            String res = null;
            while (true) {
                res = JOptionPane.showInputDialog(attendanceManager.getCanvas(), (res != null) ? "\"" + res + "\" is not a valid date.\n" : "" + "Enter a new date:");
                if (res != null) {
                    if (attendanceManager.sheetWrapper.checkValidDate(res)) {
                        Settings.setDate(res);
                        attendanceManager.sheetWrapper.updateDate();
                        break;
                    }
                } else {
                    break;
                }
            }
            lock = false;
        }).start());

        actions.put(BarcodeUtils.getBarcodeByName("Sign In/Out by Name"), () -> new Thread(() -> {
            lock = true;
            cancel:
            {
                List<Row> rows;
                String name = null;
                do {
                    name = JOptionPane.showInputDialog((name != null ? "That name is not present.\n" : "") + "Enter the last name of the member:");
                    if (name == null) break cancel;
                } while ((rows = attendanceManager.sheetWrapper.getRowsByLastName(name)).isEmpty());

                if (rows.size() > 1) {
                    String firstName = null;
                    do {
                        firstName = JOptionPane.showInputDialog((firstName != null ? "That name is not present.\n" : "") + "There are multiple people with that last name!\nEnter the first name of the member:");
                        if (firstName == null) break cancel;
                    } while (attendanceManager.sheetWrapper.getRowByFullName(firstName, name) == null);

                    if (Settings.getLoginType() == LoginType.IN_ONLY) {
                        attendanceManager.sheetWrapper.setPresentByFullName(firstName, name, true);
                        JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed in.");
                    } else if (Settings.getLoginType() == LoginType.IN_OUT) {
                        if (attendanceManager.sheetWrapper.isSignedInByFullName(firstName, name) && !attendanceManager.sheetWrapper.isSignedOutByFullName(firstName, name)) {
                            attendanceManager.sheetWrapper.signOutByFullName(firstName, name);
                            JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed out.");
                        } else {
                            attendanceManager.sheetWrapper.signInByFullName(firstName, name);
                            JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed in.");
                        }
                    }
                } else {
                    if (Settings.getLoginType() == LoginType.IN_ONLY) {
                        attendanceManager.sheetWrapper.setPresentByLastName(name, true);
                        JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed in.");
                    } else if (Settings.getLoginType() == LoginType.IN_OUT) {
                        if (attendanceManager.sheetWrapper.isSignedInByLastName(name) && !attendanceManager.sheetWrapper.isSignedOutByLastName(name)) {
                            attendanceManager.sheetWrapper.signOutByLastName(name);
                            JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed out.");
                        } else {
                            attendanceManager.sheetWrapper.signInByLastName(name);
                            JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed in.");
                        }
                    }
                }
            }
            lock = false;
        }).start());

        actions.put(BarcodeUtils.getBarcodeByName("Toggle Fullscreen"), () -> attendanceManager.setFullscreen(!attendanceManager.isFullscreen()));

        actions.put(BarcodeUtils.getBarcodeByName("Correct Name"), () -> new Thread(()-> {
//            String id = null;
//            do {
//                id = JOptionPane.showInputDialog((id != null ? "That id is not valid.\n" : "") + "Enter the ID of the name to update:");
//                if(id == null) {
//                    return;
//                }
//            } while ((rows = attendanceManager.sheetWrapper.getRowsByLastName(name)).isEmpty());
        }).start());
    }

    public void tick(long time) {
        for (Barcode b : actions.keySet()) {
            Color foreground = b.getForeground();
            if (!foreground.equals(Color.BLACK)) {
                foreground = new Color(0, Math.max(foreground.getGreen() - 8, 0), 0);
                b.setForeground(foreground);
            }
        }
    }

    Font headerFont = new Font("Arial", Font.PLAIN, 32);
    Font settingsFont = new Font("Arial", Font.BOLD, 12);

    public void render(Graphics2D g, int width, int height) {

        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(headerFont);
        String header = "Debug Menu";
        g.drawString(header, width / 2 - g.getFontMetrics().stringWidth(header) / 2, 40);

        List<String> params = getDebugInfo();

        g.setFont(settingsFont);
        int colCt = 5;
        for (int i = 0; i < params.size(); i++) {
            int col = i / colCt;

            int bx = 20 + 400 * (col);
            int by = 80 + (i % colCt) * 20;

            g.setClip(bx - 4, by - 14, 400 + 4 - 10, 12 + 4);
            g.setColor(Color.BLACK);

            int w = g.getFontMetrics().stringWidth(params.get(i)) + 6;
            if (w > 400 + 4 - 10) {
                bx += (Math.sin(System.currentTimeMillis() / 5000.0) + 1) / 2f * ((400 + 4 - 10) - w);
            }

            g.drawString(params.get(i), bx, by);
        }

        g.setClip(null);

        int rowCt = 3;
        int spacingX = 400;
        int spacingY = 300;
        int startY = 200;
        int i = 0;

        for (Barcode b : actions.keySet()) {
            int ix = i % 3;
            int iy = i / 3;
            BarcodeUtils.drawBarcode(b, g, width / 2 - b.getWidth() / 2 + (int) (ix * spacingX - (rowCt - 1) / 2f * spacingX), iy * spacingY + startY);
            i++;
        }
    }

    private List<String> getDebugInfo() {
        List<String> ret = new ArrayList<>();
        ret.addAll(Settings.getDebugInfo());
        BarcodeResult result = barcodeSensor.getLastResult();
        ret.add("Cached Scan = " + (result == null ? "null" : "\"" + result.getText() + "\""));
        result = attendanceManager.getLastResult();
        ret.add("Last Result = " + (result == null ? "null" : "\"" + result.getText() + "\""));
        ret.add("Last SID = " + (attendanceManager.getLastSID() == null ? "null" : "\"" + attendanceManager.getLastSID() + "\""));
        ret.add("Last Name = " + (attendanceManager.getLastName() == null ? "null" : "\"" + attendanceManager.getLastName() + "\""));

        ret.addAll(attendanceManager.barcodeSensor.getDebugInfo());

        return ret;
    }

    public void handleResult(BarcodeResult result) {
        if (isLocked()) {
            return;
        }

        final Barcode b = BarcodeUtils.getBarcode(result);
        if (b == null) {
            return;
        }

        final Runnable action = actions.get(b);

        if (action == null) {
            return;
        }

        action.run();
        b.setForeground(Color.GREEN);
    }

    private void playTestSound() {
        try {
            if (testSound == null) {
                testSound = AudioSystem.getClip();
            } else {
                testSound.close();
            }

            AudioInputStream inputStream = AudioSystem.getAudioInputStream(SettingsMenu.class.getClassLoader().getResource("audio/test.wav"));
            testSound.open(inputStream);
            testSound.setFramePosition(0);
            testSound.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public boolean isLocked() {
        return lock;
    }
}

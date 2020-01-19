package com.team871.ui;

import com.team871.data.Member;
import com.team871.sensing.BarcodeResult;
import com.team871.sensing.AbstractBarcodeReader;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;
import com.team871.util.Utils;
import net.sourceforge.barbecue.Barcode;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

public class SettingsMenu implements TickListener {
    private static final Font HEADER_FONT = new Font("Arial", Font.PLAIN, 32);
    private static final Font SETTINGS_FONT = new Font("Arial", Font.BOLD, 12);

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
                    try {
                        Utils.getLocalDate(res);
                        Settings.getInstance().setDate(res);
                        break;
                    } catch(Exception ignored) {}
                } else {
                    break;
                }
            }
            lock = false;
        }).start());

        actions.put(BarcodeUtils.getBarcodeByName("Sign In/Out by Name"), this::doManualLogin);

        actions.put(BarcodeUtils.getBarcodeByName("Toggle Fullscreen"), () -> attendanceManager.setFullscreen(!attendanceManager.isFullscreen()));

        actions.put(BarcodeUtils.getBarcodeByName("Correct Name"), () -> new Thread(()-> {
            final Member member = getStudent();
            if(member == null) {
                return;
            }
            String res = null;
            while (true) {
                res = JOptionPane.showInputDialog(attendanceManager.getCanvas(), (res != null) ? "\"" + res + "\" is not a valid name.\n" : "" + "Enter a new Name:");
                if (res != null) {
                    final String[] parts = res.split("\\s+");
                    if(parts.length != 2) {
                        continue;
                    }

                    member.setName(parts[0], parts[1]);
                    return;
                } else {
                    break;
                }
            }
        }).start());

        actions.put(BarcodeUtils.getBarcodeByName("Add Student"), () -> new Thread(()-> {
            String res = null;
            while (true) {
                res = JOptionPane.showInputDialog(attendanceManager.getCanvas(), (res != null) ? "\"" + res + "\" is not a valid name.\n" : "" + "Enter a new Name:");
                if (res != null) {
                    final String[] parts = res.split("\\s+");
                    if(parts.length != 2) {
                        continue;
                    }

                    Map<String, Member> byFirstName = attendanceManager.getMembersWithLastName(parts[1]);
                    if(byFirstName != null) {
                        if(byFirstName.get(parts[0]) != null) {
                            break;
                        }
                    }

                    attendanceManager.createStudent(parts[0], parts[1]);
                    return;
                } else {
                    break;
                }
            }
        }).start());
    }

    public void doManualLogin() {
        new Thread(() -> {
            lock = true;
            final Member member = getStudent();
            if(member == null) {
                lock = false;
                return;
            }

            final LocalDate date = Settings.getInstance().getDate();
            if (!member.isSignedIn(date)) {
                member.signIn(date);
                JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed in.");
            } else {
                member.signOut(date);
                JOptionPane.showMessageDialog(attendanceManager.getCanvas(), "Signed out.");
            }

            lock = false;
        }).start();
    }

    private Member getStudent() {
        Map<String, Member> students;
        String name = null;
        do {
            name = JOptionPane.showInputDialog((name != null ? "That name is not present.\n" : "") + "Enter the last name of the member:");
            if (name == null) {
                return null;
            }
        } while ((students = attendanceManager.getMembersWithLastName(name)).isEmpty());

        Member member;
        if (students.size() > 1) {
            String firstName = null;
            do {
                firstName = JOptionPane.showInputDialog((firstName != null ? "That name is not present.\n" : "") + "There are multiple people with that last name!\nEnter the first name of the member:");
                if (firstName == null) {
                    return null;
                }
            } while ((member = students.get(firstName)) == null);
        } else {
            member = students.values().stream().findFirst().get();
        }

        return member;
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

    public void render(Graphics2D g, int width, int height) {
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(HEADER_FONT);
        String header = "Debug Menu";
        g.drawString(header, width / 2 - g.getFontMetrics().stringWidth(header) / 2, 40);

        // TODO: re-implement this
        final List<String> params = Collections.emptyList();

        g.setFont(SETTINGS_FONT);
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

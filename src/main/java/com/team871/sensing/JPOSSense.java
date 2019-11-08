package com.team871.sensing;

import com.team871.exception.RobotechException;
import com.team871.ui.SettingsMenu;
import com.team871.util.BarcodeUtils;
import com.team871.util.Settings;
import jpos.JposException;
import jpos.Scanner;
import jpos.util.JposPropertiesConst;
import net.sourceforge.barbecue.Barcode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class JPOSSense extends AbstractBarcodeReader {
    private static final Logger logger = LoggerFactory.getLogger(JPOSSense.class);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 32);
    private final Scanner scanner;

    private BarcodeResult lastResult = null;

    Image scannerImage;
    Image timImage;

    int danceTimer = 0;
    Clip coolSound;

    private boolean running = false;
    private boolean ready = false;
    private Thread connectThread;

    public JPOSSense() throws RobotechException {
        String jposXmlPath = Settings.getInstance().getJposXmlPath();
        logger.info("YeahTim! -> " + getClass().getClassLoader().getResource("audio/tim.wav"));
        logger.info("Looking for jpos.xml at " + jposXmlPath);
        System.setProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, jposXmlPath);

        try {
            scanner = new Scanner();
            scanner.addErrorListener(e -> logger.warn("Scanner error: " + e.toString()));
            scanner.addStatusUpdateListener(e -> logger.info("Scanner status change: " + e.toString()));

            scanner.addDataListener(e -> {
                try {
                    final Scanner scn = (Scanner) e.getSource();
                    final String data = new String(scn.getScanData());

                    enqueueResult(data);
                    scn.setDataEventEnabled(true);
                } catch (Exception ex) {
                    logger.error("Error reading data from scanner.", ex);
                }
            });

            scanner.open("ZebraAllScanners");
        } catch (JposException e) {
            logger.error("Failed to create scanner!", e);
            throw new RobotechException("Failed to create and open scanner", e);
        }

        try {
            scannerImage = ImageIO.read(getClass().getResource("img/cool image.png"));
            timImage = ImageIO.read(getClass().getResource("img/tim.jpg"));
        } catch (Exception e) {
            logger.error("Failed to load exciting extra images", e);
        }

        beginConnect();
    }

    private void beginConnect() {
        if(ready) {
            return;
        }

        running = true;
        connectThread = new Thread(() -> {
            while (running) {
                logger.info("Connecting to scanner...");
                try {
                    scanner.claim(1000);
                    break;
                } catch (JposException e) {
                    logger.warn("Could not connect (" + e.getMessage() + "). Retrying in 2s...");
                }
            }

            if(!running) {
                return;
            }

            logger.info("Connected to scanner!");
            try {
                scanner.setDeviceEnabled(true);
                scanner.setDataEventEnabled(true);
                scanner.checkHealth(1);
            } catch (JposException e) {
                logger.info("Error initializing device after connection.", e);
                ready = false;
                return;
            }

            ready = true;
        });

        connectThread.setDaemon(true);
        connectThread.start();
    }

    @Override
    public Collection<? extends String> getDebugInfo() {
        final List<String> ret = new ArrayList<>();
        ret.add("Scanner = " + scanner);
        if (scanner != null) {
            ret.add("Scanner State = " + scanner.getState());
            try {
                if (scanner.getClaimed()) {
                    ret.add("Scanner Name = " + scanner.getPhysicalDeviceName());
                    ret.add("Scanner Health = " + scanner.getCheckHealthText());
                } else {
                    ret.add("Scanner Not Claimed");
                }
            } catch (JposException e) {
                logger.error("Failed top get scanner debug info", e);
            }
        }

        return ret;
    }

    @Override
    public void tick(long time) {
        if (danceTimer > 0) {
            danceTimer--;
        }

        final BarcodeResult newResult = getNextResult();
        if (newResult == null) {
            return;
        }

        final BarcodeResult oldResult = lastResult;
        lastResult = newResult;

        fireScannedEvent(newResult, (oldResult == null || !newResult.getText().equals(oldResult.getText())));
    }

    @Override
    public void shutdown() {
        running = false;
        try {
            connectThread.join();
        } catch (InterruptedException e) {
            logger.error("Failed to wait for connect thread to end", e);
        }
    }

    @Override
    public void render(Graphics2D g, int width, int height) {
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.GREEN);

        g.setColor(Color.BLUE);
        String s = "Scan a Barcode!";
        g.setFont(FONT);

        g.setColor(Color.WHITE);
        int sWidth = g.getFontMetrics().stringWidth(s);
        int pos = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            if (Settings.getInstance().getFun()) {
                g.setColor(rainbowColor(0.005, i * -50));
            }

            int xOfs = !Settings.getInstance().getFun() ? 0 : (int) (Math.cos((System.currentTimeMillis() + 50 * i) / 200.0) * 5);
            int yOfs = !Settings.getInstance().getFun() ? 0 : (int) (Math.sin((System.currentTimeMillis() + 50 * i) / 200.0) * 5);
            g.drawString(ch, (width / 2) - (sWidth / 2) + xOfs + pos, 50 + yOfs);
            pos += g.getFontMetrics().stringWidth(ch);
        }

        Barcode b = BarcodeUtils.getBarcodeByName("Sign In/Out by Name");
        BarcodeUtils.drawBarcode(b, g, (width / 2) - (b.getWidth() / 2), 100);

        b = BarcodeUtils.getBarcodeByName("Correct Name");
        BarcodeUtils.drawBarcode(b, g, (width / 2) - (b.getWidth() / 2), 400);
    }

    private static Color rainbowColor(double frequency, int timeOffset) {
        long i = System.currentTimeMillis() + timeOffset;

        float red = (float) (Math.sin(frequency * i + 0) * 127 + 128);
        float green = (float) (Math.sin(frequency * i + 2) * 127 + 128);
        float blue = (float) (Math.sin(frequency * i + 4) * 127 + 128);

        return new Color(red / 255f, green / 255f, blue / 255f);
    }

    public void dance() {
        if (!Settings.getInstance().getFun()) {
            return;
        }

        danceTimer = 240;
        playGoodAudio();
    }

    private void playGoodAudio() {
        try {
            if (coolSound == null) {
                coolSound = AudioSystem.getClip();
            } else {
                coolSound.close();
            }

            AudioInputStream inputStream = AudioSystem.getAudioInputStream(SettingsMenu.class.getClassLoader().getResource("dance" + new Random().nextInt(3) + ".wav"));
            coolSound.open(inputStream);
            coolSound.setFramePosition(0);
            coolSound.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}

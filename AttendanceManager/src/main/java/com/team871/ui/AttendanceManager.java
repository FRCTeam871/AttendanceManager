package com.team871.ui;

import com.team871.sensing.BarcodeResult;
import com.team871.sensing.GenericSense;
import com.team871.sensing.JPOSSense;
import com.team871.sensing.ResultListener;
import com.team871.util.BarcodeUtils;
import com.team871.util.ClasspathUtils;
import com.team871.util.Settings;
import org.apache.poi.ss.usermodel.Row;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dave
 * i know this code is terrible
 * once i get a prototype working ill make it good
 */
public class AttendanceManager implements ResultListener, KeyListener, WindowListener {

    Frame frame;
    private boolean running;
    private int fps;
    private int tps;

    private int time = 0;

    GenericSense barcodeSensor;

    int flashTimer = 0;
    int flashTimerMax = 30;

    BarcodeResult lastResult;
    String lastSID = "???";
    String lastName = "";

    SheetWrapper sheetWrapper;
    private boolean enteringNewSID = false;

    SettingsMenu settings;
    private boolean settingsMode = false;
    private Clip yeatim;

    private int clearTimerMax = 60 * 4;
    private int clearTimer = clearTimerMax;

    public static void main(String[] args){
        new AttendanceManager(args);
    }

    private AttendanceManager(String[] args){
        System.out.println("inJar = " + Settings.inJar());
        if(Settings.inJar()) {
            try {
                ClasspathUtils.loadJarDll("lib/CSJPOSScanner64.dll");
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        init(args);
        run();
    }

    private void init(String[] args){

        Settings.init();

        if(args.length > 0){
            Settings.setPrefsFile(new File(args[0]));
        }else{
            Settings.setPrefsFile(Settings.getDefaultPrefsFile());
        }

        barcodeSensor = new JPOSSense();

        frame = new Frame();
        settings = new SettingsMenu(this, barcodeSensor);

        barcodeSensor.addListener(this);

        sheetWrapper = new SheetWrapper(Settings.getSheetPath());
        frame.addMouseWheelListener(sheetWrapper);

        frame.addKeyListener(this);
        frame.addWindowListener(this);

        frame.setVisible(true);

        frame.canvas.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown() && e.isAltDown()){
                    playYeaTim();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }

    private void run(){
        long last = System.nanoTime();
        long now = System.nanoTime();

        double delta = 0d;

        double nsPerTick = 1e9 / 60d;

        long timer = System.currentTimeMillis();

        int frames = 0;
        int ticks = 0;

        running = true;

        while(running){
            now = System.nanoTime();

            long diff = now - last;

            delta += diff / nsPerTick;

            boolean shouldRender = true;

            while(delta >= 1){
                delta--;
                tick();
                ticks++;
                shouldRender = true;
            }

            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {}

            if(shouldRender){
                render();
                frames++;
            }

            last = now;

            if(System.currentTimeMillis() - timer >= 1000){
                timer = System.currentTimeMillis();
                fps = frames;
                tps = ticks;
                frames = 0;
                ticks = 0;

                frame.setTitle("Attendance UI | " + fps + " FPS " + tps + " TPS");
            }

        }
    }

    private void tick() {
        if(flashTimer > 0) flashTimer--;

        barcodeSensor.update();

        sheetWrapper.tick(time);
        settings.tick();

        if(clearTimer > 0){
            clearTimer--;
            if(clearTimer == 0){
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
        Graphics2D g = frame.getCanvas().getRenderGraphics();

        if(settingsMode){
            renderSettings(g);
            frame.paint();
            return;
        }

        int padding = 16;

        if(frame.hasFocus()){
            g.setColor(Color.RED);
        }else if(flashTimer > 0){
            Color c1 = Color.DARK_GRAY;
            Color c2 = Color.GREEN;
            float thru = flashTimer / (float)flashTimerMax;
            int r = (int)(c1.getRed() + thru * (c2.getRed() - c1.getRed()));
            int gr = (int)(c1.getGreen() + thru * (c2.getGreen() - c1.getGreen()));
            int b = (int)(c1.getBlue() + thru * (c2.getBlue() - c1.getBlue()));
            Color lerp = new Color(r, gr, b);
            g.setColor(lerp);
        }else {
            g.setColor(Color.DARK_GRAY);
        }

        g.fillRect(0, 0, frame.getCanvas().getDimensions().width, frame.getCanvas().getDimensions().height);

        Dimension dim = frame.getCanvas().getDimensions();

        Rectangle camRect = new Rectangle(padding, padding, (int)(dim.width * 0.4), (int)(dim.height * 0.4));
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(camRect.x, camRect.y, camRect.width, camRect.height);

        g.setColor(Color.BLUE);
        g.drawRect(100 + (int)(50 * Math.sin(time / 10f)), 100 + (int)(50 * Math.cos(time / 10f)), 20, 20);

        AffineTransform tr = g.getTransform();
        g.setClip(camRect);
        g.translate(camRect.x, camRect.y);
        barcodeSensor.renderPreview(g, camRect.width, camRect.height);
        g.setTransform(tr);
        g.setClip(null);


        Rectangle infoRect = new Rectangle((int)(dim.width - dim.width * 0.5 - padding), padding, (int)(dim.width * 0.5), (int)(dim.height * 0.4));
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(infoRect.x, infoRect.y, infoRect.width, infoRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(infoRect.x, infoRect.y, infoRect.width, infoRect.height);

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 32));

        List<String> lines = new ArrayList<>();
        lines.add("Scanned: " + (lastResult == null ? "???" : lastResult.getText()));
        lines.add("SID (hash): " + lastSID);
        if(!lastSID.equalsIgnoreCase("???")) lines.add("Name: " + lastName);

        for(int i = 0; i < lines.size(); i++){
            g.drawString(lines.get(i), infoRect.x + 10, infoRect.y + 32 + 32*i);
        }

        Rectangle tableRect = new Rectangle(padding, (int)(dim.height * 0.4 + padding + padding), (int)(dim.width - padding*2), (int)((dim.height) - (dim.height * 0.4 + padding + padding) - padding));
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);

        g.setStroke(new BasicStroke(1f));

        tr = g.getTransform();
        g.setClip(tableRect);
        g.translate(tableRect.x, tableRect.y);

        sheetWrapper.drawTable(g, tableRect.width, tableRect.height, time);

        g.setTransform(tr);


        frame.paint();
    }

    private void renderSettings(Graphics2D g) {
        settings.render(g, frame.getCanvas().getWidth(), frame.getCanvas().getHeight());
    }

    @Override
    public void changed(BarcodeResult result) {
        if(enteringNewSID) return; //don't accept new scans if setting up new SID

        System.out.println("Scanned: " + result.getText());

        if(settingsMode && settings.isLocked()) return;

        if(BarcodeUtils.isSettingsCommand(result)) {
            return;
        }

        if(result.getText().startsWith("A871L%4$9Z-") /*admin prefix*/){
            return;
        }

        if(settingsMode) return;


        clearTimer = clearTimerMax;



        String osid = result.getText().replaceFirst("^0+(?!$)", ""); // remove leading zeros;
        String sid;

        if(isValidSID(osid)) {
            lastResult = new BarcodeResult("(hidden student id)", result.getTime());
        }else{
            lastResult = result;
        }

        lastName = "...";

        if(isValidSID(osid)) {
            String newSid = osid;
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update(newSid.getBytes());
                String encryptedString = new String(messageDigest.digest());
                encryptedString = newSid.hashCode() + "";
                System.out.println("hash = " + encryptedString);
                //newSid = encryptedString;
            }catch(Exception e){
                e.printStackTrace();
            }

            sid = newSid;

            lastSID = sid;
            if(sheetWrapper.getRowBySID(sid) != null) {
                if (Settings.getMode() == Mode.IN_ONLY) {
                    if (!sheetWrapper.isPresent(sid)) {
                        sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                        if(barcodeSensor instanceof JPOSSense){
                            ((JPOSSense) barcodeSensor).dance();
                        }

                        if (sheetWrapper.setPresent(sid, true)) {
                            flashTimer = flashTimerMax;
                        }

                    }
                } else if (Settings.getMode() == Mode.IN_OUT) {
                    sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                    if(barcodeSensor instanceof JPOSSense){
                        ((JPOSSense) barcodeSensor).dance();
                    }
                    if (!sheetWrapper.isSignedIn(sid) && !sheetWrapper.isSignedOut(sid)) {
                        if (sheetWrapper.signInBySID(sid)) {
                            flashTimer = flashTimerMax;
                        }
                    }else if(sheetWrapper.isSignedIn(sid)){
                        if(sheetWrapper.signOutBySID(sid)){
                            flashTimer = flashTimerMax;
                        }
                    }

                }
                lastName = sheetWrapper.sidToName(sid);
            }else if(!enteringNewSID){
                enteringNewSID = true;
                new Thread(() -> {
                    int response = JOptionPane.showConfirmDialog(frame.getCanvas(), "The scanned ID is not present in the system.\nAdd it?", frame.frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    cancel: if(response == JOptionPane.YES_OPTION){
                        List<Row> rows;
                        String name = null;
                        do {
                            name = JOptionPane.showInputDialog((name != null ? "That name is not present.\n" : "") + "Enter the last name for the member associated with this ID:");
                            if(name == null) break cancel;
                        } while((rows = sheetWrapper.getRowWithNoSIDByLastName(name)).isEmpty());

                        if(rows.size() > 1){
                            Row row;
                            String firstName = null;
                            do {
                                firstName = JOptionPane.showInputDialog((firstName != null ? "That name is not present.\n" : "") + "There are multiple people with that last name!\nEnter the first name for the member associated with this ID:");
                                if(firstName == null) break cancel;
                            } while((row = sheetWrapper.getRowWithNoSIDByFullName(firstName, name)) == null);

                            sheetWrapper.setSIDByFullName(firstName, name, sid);
                        }else {
                            sheetWrapper.setSIDByLastName(name, sid);
                        }

                        if (Settings.getMode() == Mode.IN_ONLY) {
                            if (!sheetWrapper.isPresent(sid)) {
                                sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                                if(barcodeSensor instanceof JPOSSense){
                                    ((JPOSSense) barcodeSensor).dance();
                                }

                                if (sheetWrapper.setPresent(sid, true)) {
                                    flashTimer = flashTimerMax;
                                }

                            }
                        } else if (Settings.getMode() == Mode.IN_OUT) {
                            sheetWrapper.highlightRow(sheetWrapper.getRowBySID(sid));
                            if(barcodeSensor instanceof JPOSSense){
                                ((JPOSSense) barcodeSensor).dance();
                            }
                            if (!sheetWrapper.isSignedIn(sid) && !sheetWrapper.isSignedOut(sid)) {
                                if (sheetWrapper.signInBySID(sid)) {
                                    flashTimer = flashTimerMax;
                                }
                            }else if(sheetWrapper.isSignedIn(sid)){
                                if(sheetWrapper.signOutBySID(sid)){
                                    flashTimer = flashTimerMax;
                                }
                            }
                        }
                    }
                    lastName = sheetWrapper.sidToName(sid);
                    enteringNewSID = false;
                }).start();
            }
        }else{
            lastSID = "INVALID";
            if(lastResult.getText().equals("871")){
                lastSID = "YEA";
                lastName = "TIM";
                playYeaTim();
            }
        }
    }

    @Override
    public void scanned(BarcodeResult result) {

        if(settingsMode && settings.isLocked()) return;

        if(BarcodeUtils.isSettingsCommand(result)){
            settings.handleResult(result);
            return;
        }

        if(result.getText().startsWith("A871L%4$9Z-") /*admin prefix*/){
            handleAdmin(result);
            return;
        }
    }

    private void handleAdmin(BarcodeResult result) {
        String cmd = result.getText().substring("A871L%4$9Z-".length());
        //System.out.println(cmd);
        switch(cmd){
            case "SETTINGS":
                settingsMode = !settingsMode;
                break;
        }
    }

    boolean isValidSID(String test){
        if(!test.matches("^F?\\d+(\\d+)?$")) return false; //is numeric
        if(test.length() < 5 || test.length() > 7) return false; // must be 5 or 6 digits

        return true;
    }

    void showSaveDialog(){

        sheetWrapper.showNotSignedOutDialog();

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(sheetWrapper.getFile());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.showOpenDialog(frame.getCanvas());
        File f = chooser.getSelectedFile();

        boolean success = sheetWrapper.save(f);

        if(success) {
            JOptionPane.showMessageDialog(frame.getCanvas(), "Attendance saved.", frame.frame.getTitle(), JOptionPane.INFORMATION_MESSAGE);
        }else{
            JOptionPane.showMessageDialog(frame.getCanvas(), "Failed to save attendance (see console).", frame.frame.getTitle(), JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if(e.getKeyCode() == KeyEvent.VK_S && e.isControlDown()){
//            System.out.println("Ctrl+S");
            int result = JOptionPane.showConfirmDialog(frame.getCanvas(), "Do you want to save?", frame.frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if(result == JOptionPane.YES_OPTION) {
                showSaveDialog();
            }
        }else if(e.getKeyCode() == KeyEvent.VK_F5 && settingsMode){
            settingsMode = false;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void windowOpened(WindowEvent e) {}

    @Override
    public void windowClosed(WindowEvent e) {}

    @Override
    public void windowIconified(WindowEvent e) {}

    @Override
    public void windowDeiconified(WindowEvent e) {}

    @Override
    public void windowActivated(WindowEvent e) {}

    @Override
    public void windowDeactivated(WindowEvent e) {}

    @Override
    public void windowClosing(WindowEvent e) {
        if(sheetWrapper.hasUnsaved()){
            int result = JOptionPane.showConfirmDialog(frame.getCanvas(), "You have unsaved changes!\nDo you want to save?", frame.frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

            switch(result){
                case JOptionPane.YES_OPTION:
                    showSaveDialog();
                    int result2 = JOptionPane.showConfirmDialog(frame.getCanvas(), "Exit?", frame.frame.getTitle(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if(result2 == JOptionPane.YES_OPTION){
                        exit(0);
                    }
                    break;
                case JOptionPane.NO_OPTION:
                    exit(0);
                    break;
                default: // cancel or x-out
                    // don't do anything
                    break;
            }
        }else{
            exit(0);
        }
    }

    public static void exit(int status){
        System.exit(status);
    }

    public BarcodeResult getLastResult(){
        return lastResult;
    }

    public String getLastSID(){
        return lastSID;
    }

    public String getLastName(){
        return lastName;
    }

    private void playYeaTim(){
        try {
            yeatim = AudioSystem.getClip();

            AudioInputStream inputStream = AudioSystem.getAudioInputStream(SettingsMenu.class.getClassLoader().getResource("audio/tim.wav"));
            yeatim.open(inputStream);
            yeatim.setFramePosition(0);
            yeatim.start();
        }catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

}

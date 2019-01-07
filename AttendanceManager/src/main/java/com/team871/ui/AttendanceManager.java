package com.team871.ui;

import com.team871.sensing.BarcodeResult;
import com.team871.sensing.GenericSense;
import com.team871.sensing.JPOSSense;
import com.team871.sensing.ResultListener;
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
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
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
            } catch (IOException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
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

//        new Thread(() -> {
//            try {
//                Webcam webcam = Webcam.getWebcams().get(0);
////              webcam.setCustomViewSizes(new Dimension[]{new Dimension(1280,720)});
////              webcam.setViewSize(new Dimension(1280,720));
//                webcam.setViewSize(WebcamResolution.QVGA.getSize());
//                webcam.open(true);
//
//                imageProvider = new WebcamImageProvider(webcam);
//            }catch(Exception e){
//                e.printStackTrace();
//            }
//        }).start();

//        try {
//            imageProvider = new FileImageProvider(new File("C:\\barcode.png"));
//        }catch(Exception e){
//            e.printStackTrace();
//        }

//        OneDReader reader = new MultiFormatOneDReader(new HashMap(){{put(DecodeHintType.TRY_HARDER, Boolean.TRUE);}});
//        barcodeSensor = new ImageSense(reader, imageProvider);

//        barcodeSensor = new KeyboardSense();
//        frame.addKeyListener((KeyboardSense)barcodeSensor);

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

//        if(time % 30 == 0){ // update every 30 ticks (twice per second)
//            BufferedImage img = imageProvider.getImage();
//            img = doFiltering(img);
//            barcodeSensor.update(img);
//        }

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

    private void render(){

        Graphics2D g = frame.getCanvas().getRenderGraphics();
//        g.clearRect(0, 0, frame.getCanvas().getDimensions().width, frame.getCanvas().getDimensions().height);

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
//        System.out.println(tableRect.height);
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);

        g.setStroke(new BasicStroke(1f));

        tr = g.getTransform();
        g.setClip(tableRect);
        g.translate(tableRect.x, tableRect.y);

//        g.fillRect(0, 0, 1000, 1000);

        sheetWrapper.setDimension(tableRect);
        sheetWrapper.drawTable(g, time);

        g.setTransform(tr);


        frame.paint();
    }

    private void renderSettings(Graphics2D g) {
        settings.render(g, frame.getCanvas().getWidth(), frame.getCanvas().getHeight());
    }

    public int getTime() {
        return time;
    }

    private BufferedImage doFiltering(BufferedImage src){
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);

        RescaleOp rescale = new RescaleOp(2.0f,20.0f, null);
        out = rescale.filter(src,null);

        for(int x = 0; x < src.getWidth(); x++){
            for(int y = 0; y < src.getHeight(); y++){
                int rgb = out.getRGB(x, y);
                int r = (rgb >> 16) & 0x000000FF;
                int g = (rgb >>8 ) & 0x000000FF;
                int b = (rgb) & 0x000000FF;
                float[] hsv = new float[3];
                Color.RGBtoHSB(r, g, b, hsv);
                out.setRGB(x, y, Color.HSBtoRGB(0, 0, (float)scaleValue(hsv[2])));
//                if(hsv[2] < 0.5){
//                    out.setRGB(x, y, Color.HSBtoRGB(0, 0, 0));
//                }else if(hsv[2] < 0.8){
//                    out.setRGB(x, y, Color.HSBtoRGB(0, 0, 0.25f));
//                }else {
//                    out.setRGB(x, y, Color.HSBtoRGB(0, 0, 1));
//                }
            }
        }


        return out;
    }

    double scaleValue(double in){
//        double out = (Math.pow(2*in - 1 + 0.1, 1/1.32) + 1) / 2.0;

        double out = 0;
        if(in < 0.75){
            out = 0;
        }else{
            out = 1;
        }

        if(out < 0) out = 0;
        if(out > 1) out = 1;

        return out;
    }

    BufferedImage flip(BufferedImage img){
        AffineTransform at = new AffineTransform();
        at.concatenate(AffineTransform.getScaleInstance(-1, 1));
        at.concatenate(AffineTransform.getTranslateInstance(-img.getWidth(), 0));
        return createTransformed(img, at);
    }

    private BufferedImage createTransformed(BufferedImage image, AffineTransform at){
        BufferedImage newImage = new BufferedImage(
                image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = newImage.createGraphics();
        g.transform(at);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImage;
    }

    @Override
    public void changed(BarcodeResult result) {
        if(enteringNewSID) return; //don't accept new scans if setting up new SID

        System.out.println("Scanned: " + result.getText());

        if(settingsMode && settings.isLocked()) return;

        if(result.getText().startsWith(SettingsMenu.SETTINGS_CODE_PREFIX)){
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

        if(result.getText().startsWith(SettingsMenu.SETTINGS_CODE_PREFIX)){
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
//            if (yeatim == null){
//                yeatim = AudioSystem.getClip();
//            }else{
//                yeatim.close();
//            }
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

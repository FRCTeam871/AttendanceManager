package com.team871.sensing;

import com.team871.util.BarcodeUtils;
import jpos.JposException;
import jpos.Scanner;
import jpos.events.*;
import jpos.util.JposPropertiesConst;
import com.team871.util.Settings;
import com.team871.ui.SettingsMenu;
import net.sourceforge.barbecue.Barcode;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class JPOSSense extends AbstractBarcodeReader implements ErrorListener, DataListener, StatusUpdateListener {
    private static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 32);

    String buffer = "";
    boolean send = false;
    long time;

    Scanner scanner;

    Image img;
    Image img2;

    int danceTimer = 0;
    Clip coolSound;

    public JPOSSense() {
        String jposXmlPath = Settings.getJposXmlPath();
        System.out.println(getClass().getClassLoader().getResource("audio/tim.wav"));
        System.out.println("Looking for jpos.xml at " + jposXmlPath);
        System.setProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, jposXmlPath);

        try {
            scanner = new Scanner();
            scanner.addErrorListener(this);
            scanner.addDataListener(this);
            scanner.addStatusUpdateListener(this);
            scanner.open("ZebraAllScanners");

            startConnectThread();

        }catch(Exception e){
            e.printStackTrace();
            if(e instanceof JposException){
                ((JposException) e).getOrigException().printStackTrace();
            }
        }

        try {
            img = ImageIO.read(JPOSSense.class.getClassLoader().getResource("img/cool image.png"));
        }catch(Exception e){}
        try {
            img2 = ImageIO.read(JPOSSense.class.getClassLoader().getResource("img/tim.jpg"));
        }catch(Exception e){}
    }

    void startConnectThread(){
        new Thread(() -> {
            while(true){
                System.out.println("Connecting to scanner...");
                try{
                    scanner.claim(1000);
                    break;
                }catch(JposException e){
                    System.err.println("Could not connect (" + e.getMessage() + "). Retrying in 2s...");
                }
                try {
                    Thread.sleep(2000);
                }catch (InterruptedException e){}

            }

            System.out.println("Connected to scanner!");
            try {
                scanner.setDeviceEnabled(true);
                scanner.setDataEventEnabled(true);
                scanner.checkHealth(1);
            }catch(JposException e){
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected BarcodeResult findResult() {
        String ret = null;

        if(send) {
            send = false;
            ret = buffer;
            buffer = "";
        }
        return ret == null ? null : new BarcodeResult(ret, time);
    }

    @Override
    public void renderPreview(Graphics2D g, int width, int height) {
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.GREEN);

        g.setColor(Color.BLUE);
        String s = "Scan a Barcode!";
        g.setFont(FONT);

        g.setColor(Color.WHITE);
        int sWidth = g.getFontMetrics().stringWidth(s);
        int pos = 0;
        for(int i = 0; i < s.length(); i++){
            String ch = s.substring(i, i+1);
            if(Settings.getFun()) g.setColor(rainbowColor(0.005, i * -50));
            int xOfs = !Settings.getFun() ? 0 : (int)(Math.cos((System.currentTimeMillis() + 50*i) / 200.0) * 5);
            int yOfs = !Settings.getFun() ? 0 : (int)(Math.sin((System.currentTimeMillis() + 50*i) / 200.0) * 5);
            g.drawString(ch, (width/2) - (sWidth/2) + xOfs + pos, 50 + yOfs);
            pos += g.getFontMetrics().stringWidth(ch);
        }

        Barcode b = BarcodeUtils.getBarcodeByName("Sign In/Out by Name");
        BarcodeUtils.drawBarcode(b, g, (width/2)-(b.getWidth()/2) ,100);

        b = BarcodeUtils.getBarcodeByName("Toggle Fullscreen");
        BarcodeUtils.drawBarcode(b, g, (width/2) - (b.getWidth()/2), height-100);
    }

    @Override
    public void errorOccurred(ErrorEvent e) {

    }

    @Override
    public void dataOccurred(DataEvent e) {
        try {
            Scanner scn = (Scanner) e.getSource();
            String data = new String(scn.getScanData());
            //System.out.println(data);
            buffer = data;
            send = true;
            scn.setDataEventEnabled(true);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void statusUpdateOccurred(StatusUpdateEvent e) {

    }

    @Override
    public Collection<? extends String> getDebugInfo() {
        List<String> ret = new ArrayList<>();
        ret.add("Scanner = " + scanner);
        if(scanner != null){
            try {
                ret.add("Scanner State = " + scanner.getState());
                if(scanner.getClaimed()) {
                    ret.add("Scanner Name = " + scanner.getPhysicalDeviceName());
                    ret.add("Scanner Health = " + scanner.getCheckHealthText());
                }else{
                    ret.add("Scanner Not Claimed");
                }
            }catch(JposException e){
                e.printStackTrace();
            }
        }
        return ret;
    }

    public static Color rainbowColor(double frequency, int timeOffset){

        long i = System.currentTimeMillis() + timeOffset;

        float red   = (float) (Math.sin(frequency*i + 0) * 127 + 128);
        float green = (float) (Math.sin(frequency*i + 2) * 127 + 128);
        float blue  = (float) (Math.sin(frequency*i + 4) * 127 + 128);

//        System.out.println(red + " " + green + " " + blue);

        return new Color(red / 255f, green / 255f, blue / 255f);
    }

    @Override
    public void update() {
        super.update();
        if(danceTimer > 0) danceTimer--;
    }

    public void dance(){
        if(!Settings.getFun()) return;
        danceTimer = 240;
        playGoodAudio();
    }

    private void playGoodAudio(){
        try {
            if (coolSound == null){
                coolSound = AudioSystem.getClip();
            }else{
                coolSound.close();
            }

            AudioInputStream inputStream = AudioSystem.getAudioInputStream(SettingsMenu.class.getClassLoader().getResource("dance" + new Random().nextInt(3) + ".wav"));
            coolSound.open(inputStream);
            coolSound.setFramePosition(0);
            coolSound.start();
        }catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

}

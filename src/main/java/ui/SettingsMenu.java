package ui;

import javafx.util.Pair;
import net.sourceforge.barbecue.Barcode;
import net.sourceforge.barbecue.BarcodeException;
import net.sourceforge.barbecue.linear.code39.Code39Barcode;
import net.sourceforge.barbecue.output.OutputException;
import sensing.BarcodeResult;
import sensing.GenericSense;

import javax.sound.sampled.*;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.HashMap;

public class SettingsMenu {

    public static final String SETTINGS_CODE_PREFIX = "SET-";

    private HashMap<Pair<String, String>, Runnable> actions;
    private List<Barcode> barcodes;
    private Clip testSound;
    private Main main;
    private GenericSense barcodeSensor;

    public SettingsMenu(Main main, GenericSense barcodeSensor){
        this.main = main;
        this.barcodeSensor = barcodeSensor;
        registerActions();
        createBarcodes();
    }

    private void registerActions() {
        actions = new LinkedHashMap<>();
        actions.put(new Pair<>("T1", "Test Scanner"), () -> {
            System.out.println("test 1!");
            playTestSound();
        });
    }

    private void createBarcodes() {
        barcodes = new ArrayList<>();
        for(Pair<String, String> pair : actions.keySet()){
            String code = pair.getKey();
            String label = pair.getValue();
            try {
                Barcode barcode = new Code39Barcode(SETTINGS_CODE_PREFIX + code, false);
                barcode.setBackground(Color.LIGHT_GRAY);
                barcode.setForeground(Color.BLACK);
                barcode.setDrawingText(true);
                setBarcodeLabel(barcode, label);

                barcodes.add(barcode);
            } catch (BarcodeException e) {
                e.printStackTrace();
            }
        }
    }

    public void tick(){
        for(Barcode b : barcodes){
            Color foreground = b.getForeground();
            if(!foreground.equals(Color.BLACK)){
                foreground = new Color(0, Math.max(foreground.getGreen()-8, 0), 0);
                b.setForeground(foreground);
            }
        }
    }

    Font headerFont = new Font("Arial", Font.PLAIN, 32);
    Font settingsFont = new Font("Arial", Font.BOLD, 12);

    public void render(Graphics2D g, int width, int height){

        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(headerFont);
        String header = "Debug Menu";
        g.drawString(header, width / 2 - g.getFontMetrics().stringWidth(header)/2, 40);

        List<String> params = getDebugInfo();

        g.setFont(settingsFont);
        int colCt = 5;
        for(int i = 0; i < params.size(); i++){
            int col = i / colCt;

            int bx = 20 + 400*(col);
            int by = 80 + (i%colCt) * 20;

            g.setColor(Color.BLUE);
            g.drawRect(bx-4, by - 14, 400+4 - 10, 12+4);
//            g.setClip(bx-4, by - 14, 400+4 - 10, 12+4);
            g.setColor(Color.BLACK);

            int w = g.getFontMetrics().stringWidth(params.get(i));
            if(w > 400 + 4 - 10){
                //TODO
                System.out.println(((400 + 4 - 10)-w) + " " + (Math.sin(System.currentTimeMillis() / 1000f) + 1)/2f);
                bx += (Math.sin(System.currentTimeMillis() / 1000f) + 1)/2f * ((400 + 4 - 10)-w);
            }

            g.drawString(params.get(i), bx, by);
        }

        g.setClip(null);

        int rowCt = 3;
        int spacingX = 400;
        int spacingY = 300;
        int startY = 200;
        for(int i = 0; i < barcodes.size(); i++){
            int ix = i % 3;
            int iy = i / 3;
            drawBarcode(barcodes.get(i), g, width / 2 - barcodes.get(i).getWidth()/2 + (int)(ix*spacingX - (rowCt-1)/2f*spacingX), iy * spacingY + startY);
        }
    }

    private List<String> getDebugInfo() {
        List<String> ret = new ArrayList<>();
        ret.addAll(Settings.getDebugInfo());
        BarcodeResult result = barcodeSensor.getCachedResult();
        ret.add("Cached Scan = " + (result == null ? "null" : "\"" + result.getText() + "\""));
        result = main.getLastResult();
        ret.add("Last Result = " + (result == null ? "null" : "\"" + result.getText() + "\""));
        ret.add("Last SID = " + (main.getLastSID() == null ? "null" : "\"" + main.getLastSID() + "\""));
        ret.add("Last Name = " + (main.getLastName() == null ? "null" : "\"" + main.getLastName() + "\""));

        ret.addAll(main.barcodeSensor.getDebugInfo());

        return ret;
    }

    public void drawBarcode(Barcode bar, Graphics2D g, int x, int y){
        try {
            bar.draw(g, x, y);
        }catch(OutputException e){
            e.printStackTrace();
        }
    }

    private void setBarcodeLabel(Barcode bar, String label){
        if(bar instanceof Code39Barcode){
            try{
                //delicious
                Field f = bar.getClass().getDeclaredField("label");
                f.setAccessible(true);
                f.set(bar, label);
            }catch(Exception e){
                e.printStackTrace();
            }
        }else{
            bar.setLabel(label);
        }
    }

    public void handleResult(BarcodeResult result) {
        String scan = result.getText().substring(SETTINGS_CODE_PREFIX.length());
        for(Pair<String, String> pair : actions.keySet()){
            if(pair.getKey().equals(scan)) {
                actions.get(pair).run();
                for(Barcode b : barcodes){
                    if(b.getData().equals(SETTINGS_CODE_PREFIX + scan)) b.setForeground(Color.GREEN);
                }
            }
        }
    }

    private void playTestSound(){
        try {
            if (testSound == null){
                testSound = AudioSystem.getClip();
            }else{
                testSound.close();
            }

            AudioInputStream inputStream = AudioSystem.getAudioInputStream(SettingsMenu.class.getClassLoader().getResource("test.wav"));
            testSound.open(inputStream);
            testSound.setFramePosition(0);
            testSound.start();
        }catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

}

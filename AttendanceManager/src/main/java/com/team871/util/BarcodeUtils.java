package com.team871.util;

import com.team871.sensing.BarcodeResult;
import net.sourceforge.barbecue.Barcode;
import net.sourceforge.barbecue.BarcodeException;
import net.sourceforge.barbecue.output.OutputException;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class BarcodeUtils {
    public static final String SETTINGS_CODE_PREFIX = "SET-";
    private static final Map<String, Barcode> barcodesByCommand = new HashMap<>();
    private static final Map<String, Barcode> barcodesByName = new HashMap<>();

    static {
        try {
            addBarcode("Test Scanner", "T1");
            addBarcode("Set Date", "D8");
            addBarcode("Sign In/Out by Name", "SI");
            addBarcode("Toggle Fullscreen", "FS");
        } catch (BarcodeException ex) {
            ex.printStackTrace();
        }
    }

    private static void addBarcode(String name, String command) throws BarcodeException {
        Barcode b = makeBarcode(command, name);
        barcodesByCommand.put(command, b);
        barcodesByName.put(name, b);
    }

    private static Barcode makeBarcode(String command, String label) throws BarcodeException {
        Barcode barcode = new Code39Barcode(SETTINGS_CODE_PREFIX + command, false);
        barcode.setBackground(Color.LIGHT_GRAY);
        barcode.setForeground(Color.BLACK);
        barcode.setDrawingText(true);
        barcode.setLabel(label);

        return barcode;
    }

    public static void drawBarcode(Barcode bar, Graphics2D g, int x, int y) {
        try {
            bar.draw(g, x, y);
        }catch(OutputException e){
            e.printStackTrace();
        }
    }

    public static Barcode getBarcode(BarcodeResult br) {
        return getBarcode(getSettingsCommand(br));
    }

    public static Barcode getBarcode(String code) {
        return barcodesByCommand.get(code);
    }

    public static Barcode getBarcodeByName(String name) {
        return barcodesByName.get(name);
    }

    public static String getSettingsCommand(BarcodeResult br) {
        final String text = br.getText();
        if(text == null || text.isEmpty() || !text.startsWith(SETTINGS_CODE_PREFIX)) {
            return null;
        }

        return text.substring(SETTINGS_CODE_PREFIX.length());
    }

    public static boolean isSettingsCommand(BarcodeResult br) {
        final String text = br.getText();
        return !(text == null || text.isEmpty() || !text.startsWith(SETTINGS_CODE_PREFIX));
    }
}

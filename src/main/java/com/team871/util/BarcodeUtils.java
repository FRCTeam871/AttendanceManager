package com.team871.util;

import com.team871.sensing.BarcodeResult;
import net.sourceforge.barbecue.Barcode;
import net.sourceforge.barbecue.BarcodeException;
import net.sourceforge.barbecue.output.OutputException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class BarcodeUtils {
    private static final Logger log = LoggerFactory.getLogger(BarcodeUtils.class);
    private static final String SETTINGS_CODE_PREFIX = "SET-";
    private static final String ADMIN_PREFIX = "A871L%4$9Z-";
    private static final Map<String, Barcode> barcodesByCommand = new HashMap<>();
    private static final Map<String, Barcode> barcodesByName = new HashMap<>();

    static {
        try {
            addBarcode("Test Scanner", "T1");
            addBarcode("Set Date", "D8");
            addBarcode("Sign In/Out by Name", "SI");
            addBarcode("Toggle Fullscreen", "FS");
            addBarcode("Correct Name", "CN");
        } catch (BarcodeException ex) {
            log.error("Failed to add barcode:", ex);
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
        } catch (OutputException e) {
            log.error("Failed to draw barcode", e);
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

    public static boolean isSettingsCommand(@NotNull BarcodeResult br) {
        final String text = br.getText();
        return !isNullOrEmpty(text) && text.startsWith(SETTINGS_CODE_PREFIX);
    }

    public static boolean isAdminCommand(@NotNull BarcodeResult result) {
        final String text = result.getText();
        return !isNullOrEmpty(text) && text.startsWith(ADMIN_PREFIX);
    }

    public static String getSettingsCommand(BarcodeResult br) {
        return getSuffix(br.getText(), SETTINGS_CODE_PREFIX);
    }

    public static String getAdminCommand(BarcodeResult result) {
        return getSuffix(result.getText(), ADMIN_PREFIX);
    }

    private static String getSuffix(String text, @NotNull String prefix) {
        if (isNullOrEmpty(text) || !text.startsWith(prefix)) {
            return null;
        }

        return text.substring(prefix.length());
    }

    public static boolean isNullOrEmpty(String val) {
        return val == null || val.isEmpty();
    }
}

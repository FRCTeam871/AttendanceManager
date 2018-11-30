package ui;

import net.sourceforge.barbecue.Barcode;
import net.sourceforge.barbecue.BarcodeException;
import net.sourceforge.barbecue.linear.code39.Code39Barcode;

import java.awt.*;

public class SettingsMenu {

    Barcode barcode;

    public SettingsMenu(){
        try {
            barcode = new Code39Barcode("yeet", false);
        } catch (BarcodeException e) {
            e.printStackTrace();
        }
    }

    public void render(Graphics2D g){

    }

}

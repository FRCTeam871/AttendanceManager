package com.team871.sensing;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.OneDReader;
import com.team871.provider.ImageProvider;

import java.util.List;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;

public class ImageSense extends GenericSense {

    final OneDReader reader;
    ImageProvider imgProvider;

    BufferedImage lastWebcam;
    int timeCooldown = 0;

    public ImageSense(OneDReader reader, ImageProvider provider) {
        super();
        this.reader = reader;
        this.listeners = new ArrayList<>();
        this.imgProvider = provider;
    }

    @Override
    protected BarcodeResult findResult() {
        try {
            BinaryBitmap bbm = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imgProvider.getImage())));
            Result newResult = reader.decode(bbm);
            return new BarcodeResult(newResult.getText(), System.currentTimeMillis());
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void update() {
        super.update();

        if(timeCooldown == 0 && imgProvider != null && imgProvider.isAvailable()){
            BufferedImage bi = imgProvider.getImage();
            BufferedImage biNew = new BufferedImage(bi.getWidth(null), bi.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics g = biNew.createGraphics();
            g.drawImage(bi, 0, 0, null);
            g.dispose();
            lastWebcam = biNew;
            timeCooldown--;
            if(timeCooldown == 0) timeCooldown = 5;
        }

    }

    @Override
    public void renderPreview(Graphics2D g, int width, int height) {
        if(lastWebcam != null) {

//        System.out.println(img.getWidth() + " " + img.getHeight());
//            long start = System.currentTimeMillis();
            g.drawImage(lastWebcam, 0,0, width, height, null); //TODO: why does this call take so long when using webcam? (scaling?)
//        System.out.println("took " + (System.currentTimeMillis() - start) + " " + img.getType() + " " + img.getClass());

            g.setColor(new Color(0.5f, 0.5f, 0.5f, 1f));
            String str = imgProvider.getInfo();
            g.drawString(str, 2, height - 2);
        }else{
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0,0, width, height);
            g.setColor(Color.LIGHT_GRAY);
            float thru = (System.currentTimeMillis() % 1000) / 1000f;
            String s = "Starting ImageProvider" + (thru >= 0.25f ? "." : " ") + (thru >= 0.5f ? "." : " ") + (thru >= 0.75f ? "." : " ");
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 32));
            g.drawString(s, width/2 - g.getFontMetrics().stringWidth(s)/2, height/2);
        }
    }

    @Override
    public Collection<? extends String> getDebugInfo() {
        List<String> ret = new ArrayList<>();
        ret.add("Image Provider = " + imgProvider);
        if(imgProvider != null) {
            ret.add("Provider Avail = " + imgProvider.isAvailable());
            ret.add("Provider Info = " + imgProvider.getInfo());
        }
        return ret;
    }

}

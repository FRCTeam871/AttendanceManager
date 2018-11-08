package ui;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.MultiFormatOneDReader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.HashMap;

/**
 * @author Dave
 * i know this code is terrible
 * once i get a prototype working ill make it good
 */
public class Main {

    Frame frame;
    private boolean running;
    private int fps;
    private int tps;

    private int time = 0;

    Webcam webcam;
    MultiFormatOneDReader reader;

    String lastBarcode = "";

    public static void main(String[] args){
        new Main();
    }

    private Main(){
        init();
        run();
    }

    private void init(){

        frame = new Frame();

        try {
            webcam = Webcam.getWebcams().get(0);
//            webcam.setCustomViewSizes(new Dimension[]{new Dimension(1280,720)});
//            webcam.setViewSize(new Dimension(1280,720));
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open();
        }catch(Exception e){
            e.printStackTrace();
        }

        reader = new MultiFormatOneDReader(null);
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
            }

        }
    }

    private void tick() {
        time++;
    }

    private void render(){

        Graphics2D g = frame.getCanvas().getRenderGraphics();
        g.clearRect(0, 0, frame.getCanvas().getDimensions().width, frame.getCanvas().getDimensions().height);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, frame.getCanvas().getDimensions().width, frame.getCanvas().getDimensions().height);

        Dimension dim = frame.getCanvas().getDimensions();

        Rectangle camRect = new Rectangle(20, 20, (int)(dim.width * 0.4), (int)(dim.height * 0.4));
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(camRect.x, camRect.y, camRect.width, camRect.height);

        g.setColor(Color.BLUE);
        g.drawRect(100 + (int)(50 * Math.sin(time / 10f)), 100 + (int)(50 * Math.cos(time / 10f)), 20, 20);

        BufferedImage img = webcam.getImage();
        //System.out.println(img.getWidth() + " " + img.getHeight());
        g.drawImage(img, camRect.x, camRect.y, camRect.width, camRect.height, null);
        g.setColor(Color.WHITE);
        g.drawString(String.format("%.1f fps", webcam.getFPS()), camRect.x + 2, camRect.y + camRect.height - 2);


        Rectangle infoRect = new Rectangle((int)(dim.width - dim.width * 0.5 - 20), 20, (int)(dim.width * 0.5), (int)(dim.height * 0.4));
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(infoRect.x, infoRect.y, infoRect.width, infoRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(infoRect.x, infoRect.y, infoRect.width, infoRect.height);

        String barcode = null;

        try {
            BinaryBitmap bbm = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));

            Result r = reader.decode(bbm/*, new HashMap(){{put(DecodeHintType.TRY_HARDER, Boolean.TRUE);}}*/);

            barcode = r.getText();
        } catch (NotFoundException e) {

        } catch (FormatException e) {
            e.printStackTrace();
        }

        if(barcode != null){
            lastBarcode = barcode;
        }

        g.setColor(Color.BLACK);
        g.drawString(lastBarcode, infoRect.x + 10, infoRect.y + 20);

        Rectangle tableRect = new Rectangle(20, (int)(dim.height * 0.4 + 20 + 20), (int)(dim.width - 40), (int)((dim.height) - (dim.height * 0.4 + 20 + 20) - 20));
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(8f));
        g.drawRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);
        g.setColor(Color.WHITE);
        g.fillRect(tableRect.x, tableRect.y, tableRect.width, tableRect.height);

        g.setStroke(new BasicStroke(1f));

        AffineTransform tr = g.getTransform();
        g.setClip(tableRect);
        g.translate(tableRect.x, tableRect.y);

        drawTable(g);

        g.setTransform(tr);



        frame.paint();
    }

    public int getTime() {
        return time;
    }

    private void drawTable(Graphics2D g){
        g.setColor(Color.BLUE);
        g.drawRect(10, 10, 20, 20);
    }

    private BufferedImage doFiltering(BufferedImage src){
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);

        for(int x = 0; x < src.getWidth(); x++){
            for(int y = 0; y < src.getHeight(); y++){
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0x000000FF;
                int g = (rgb >>8 ) & 0x000000FF;
                int b = (rgb) & 0x000000FF;
                float[] hsv = new float[3];
                Color.RGBtoHSB(r, g, b, hsv);
                out.setRGB(x, y, Color.HSBtoRGB(0, 0, hsv[2] * hsv[2]));
//                if(hsv[2] < 0.5){
//                    out.setRGB(x, y, Color.HSBtoRGB(0, 0, 0));
//                }else if(hsv[2] < 0.8){
//                    out.setRGB(x, y, Color.HSBtoRGB(0, 0, 0.25f));
//                }else {
//                    out.setRGB(x, y, Color.HSBtoRGB(0, 0, 1));
//                }
            }
        }

        //RescaleOp rescale = new RescaleOp(1.1f,20.0f, null);
        //out = rescale.filter(out,null);

        return out;
    }

}

package ui;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.MultiFormatOneDReader;
import ui.imageprovider.ImageProvider;
import ui.imageprovider.WebcamImageProvider;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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

    ImageProvider imageProvider;
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
        frame.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_I) {
                    try {
                        ImageIO.write(imageProvider.getImage(), "PNG", File.createTempFile("yeet", "v2_NEO.png"));
                    }catch(Exception ex){
                        ex.printStackTrace();
                    }
                }
            }
        });

        try {
            Webcam webcam = Webcam.getWebcams().get(0);
//            webcam.setCustomViewSizes(new Dimension[]{new Dimension(1280,720)});
//            webcam.setViewSize(new Dimension(1280,720));
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open();

            imageProvider = new WebcamImageProvider(webcam);
        }catch(Exception e){
            e.printStackTrace();
        }

        reader = new MultiFormatOneDReader(new HashMap(){{put(DecodeHintType.TRY_HARDER, Boolean.TRUE);}});
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

        BufferedImage img = imageProvider.getImage(); //TODO: check imageProvider.isAvailable();
        BufferedImage orig = img;
        try {
//            img = ImageIO.read(new File("C:\\Users\\Team871\\Pictures\\bar.png"));
        }catch(Exception e){
            e.printStackTrace();
        }

        img = doFiltering(img);

        //System.out.println(img.getWidth() + " " + img.getHeight());
        g.drawImage(orig, camRect.x, camRect.y, camRect.width, camRect.height, null);
        g.setColor(Color.WHITE);
        //g.drawString(String.format("%.1f fps", webcam.getFPS()), camRect.x + 2, camRect.y + camRect.height - 2); //TODO: reimplement a better way to do this


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

            g.setStroke(new BasicStroke(2f));
            ResultPoint[] points = r.getResultPoints();
            for(int i = 0; i < points.length; i++){
                ResultPoint th = points[i];
                ResultPoint next = (i == points.length - 1) ? points[0] : points[i+1];
                g.drawLine((int)th.getX(), (int)th.getY(), (int)next.getX(), (int)next.getY());
            }

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

}

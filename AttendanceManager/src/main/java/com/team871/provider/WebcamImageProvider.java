package com.team871.provider;

import com.github.sarxos.webcam.Webcam;

import java.awt.image.BufferedImage;

public class WebcamImageProvider implements ImageProvider {

    Webcam webcam;

    public WebcamImageProvider(Webcam webcam) {
        this.webcam = webcam;
    }

    @Override
    public BufferedImage getImage() {
        return webcam.getImage();
    }

    @Override
    public boolean isAvailable() {
        return webcam.isOpen();
    }

    @Override
    public String getInfo(){
        return String.format("%.1f fps", webcam.getFPS());
    }

}

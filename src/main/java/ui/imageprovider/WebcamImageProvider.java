package ui.imageprovider;

import com.github.sarxos.webcam.Webcam;
import ui.imageprovider.ImageProvider;

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

}

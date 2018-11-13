package ui.imageprovider;

import java.awt.image.BufferedImage;

public interface ImageProvider {

    BufferedImage getImage();
    boolean isAvailable();

}

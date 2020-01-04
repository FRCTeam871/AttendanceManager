package com.team871.provider;

import java.awt.image.BufferedImage;

public interface ImageProvider {

    BufferedImage getImage();
    boolean isAvailable();
    String getInfo();

}

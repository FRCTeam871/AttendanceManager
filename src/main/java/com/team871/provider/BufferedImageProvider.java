package com.team871.provider;

import java.awt.image.BufferedImage;

public class BufferedImageProvider implements ImageProvider{

    private final BufferedImage img;

    public BufferedImageProvider(BufferedImage img) {
        this.img = img;
    }

    @Override
    public BufferedImage getImage() {
        return img;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getInfo() {
        return img.toString();
    }

}

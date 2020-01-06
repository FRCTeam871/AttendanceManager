package com.team871.ui;

import java.awt.*;
import java.awt.image.BufferedImage;

public class UICanvas extends Canvas {

    BufferedImage img;

    public UICanvas(int w, int h){
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    @Override
    public void paint(Graphics g) {
        paintComponent(g);
    }

    public void paintComponent(Graphics g) {
        try{
            g.drawImage(img, 0, 0, this);
        }catch(NullPointerException e){}
    }

    @Override
    public void update(Graphics g) {
        paintComponent(g);
    }

    public Graphics2D getRenderGraphics(){
        return (Graphics2D) img.getGraphics();
    }

    public Dimension getDimensions(){
        return new Dimension(img.getWidth(), img.getHeight());
    }

    public void resizeCanvas(int w, int h){
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }
}

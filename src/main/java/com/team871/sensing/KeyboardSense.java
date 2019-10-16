package com.team871.sensing;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KeyboardSense extends AbstractBarcodeReader implements KeyListener {

    String buffer = "";
    boolean send = false;
    long time;

    @Override
    protected BarcodeResult findResult() {
        String ret = null;
//        System.out.println(buffer + " " + send);
        if(send) {
            send = false;
            ret = buffer;
            buffer = "";
        }
        return ret == null ? null : new BarcodeResult(ret, time);
    }

    @Override
    public void renderPreview(Graphics2D g, int width, int height) {
        g.setColor(Color.BLUE);
        g.fillRect(0,0, width, height);
    }

    @Override
    public void keyTyped(KeyEvent e) {
//        System.out.println(e.getKeyChar() + " " + (int)e.getKeyChar());
        if(send) return;
        if((int)e.getKeyChar() == 10){
            send = true;
            time = System.currentTimeMillis();
        }else {
            buffer += e.getKeyChar();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    @Override
    public Collection<? extends String> getDebugInfo() {
        List<String> ret = new ArrayList<>();
        ret.add("Buffer = \"" + buffer + "\"");
        ret.add("Time = " + time);
        return ret;
    }

}

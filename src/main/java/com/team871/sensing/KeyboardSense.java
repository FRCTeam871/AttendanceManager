package com.team871.sensing;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;
import java.util.List;

import static java.awt.event.KeyEvent.VK_ENTER;

public class KeyboardSense extends AbstractBarcodeReader implements KeyListener {
    private StringBuilder buffer = new StringBuilder();
    private BarcodeResult lastResult = null;

    @Override
    public void render(Graphics2D g, int width, int height) {
        g.setColor(Color.BLUE);
        g.fillRect(0,0, width, height);
        g.drawString("Buffer: " + buffer, 10, 10);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void keyTyped(KeyEvent e) {
        if((int)e.getKeyChar() == VK_ENTER) {
            enqueueResult(buffer.toString());
            buffer = new StringBuilder();
        } else {
            buffer.append(e.getKeyChar());
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
        return Collections.singletonList("Buffer = \"" + buffer + "\"");
    }

    @Override
    public void tick(long time) {

    }
}

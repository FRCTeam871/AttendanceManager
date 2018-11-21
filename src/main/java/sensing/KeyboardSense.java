package sensing;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyboardSense extends GenericSense implements KeyListener {

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
}

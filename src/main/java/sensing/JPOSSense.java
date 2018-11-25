package sensing;

import jpos.JposException;
import jpos.Scanner;
import jpos.events.*;
import jpos.util.JposPropertiesConst;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class JPOSSense extends GenericSense implements ErrorListener, DataListener, StatusUpdateListener {

    String buffer = "";
    boolean send = false;
    long time;

    Scanner scanner;

    public JPOSSense(){
        System.out.println("Looking for jpos.xml at " + System.getProperty("user.home") + "\\jpos.xml");
        System.setProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, System.getProperty("user.home") + "\\jpos.xml");
        try {
            scanner = new Scanner();
            scanner.addErrorListener(this);
            scanner.addDataListener(this);
            scanner.addStatusUpdateListener(this);
            scanner.open("ZebraAllScanners");

            startConnectThread();

        }catch(Exception e){
            e.printStackTrace();
            if(e instanceof JposException){
                ((JposException) e).getOrigException().printStackTrace();
            }
        }

    }

    void startConnectThread(){
        new Thread(() -> {
            while(true){
                System.out.println("Connecting to scanner...");
                try{
                    scanner.claim(1000);
                    break;
                }catch(JposException e){
                    System.err.println("Could not connect (" + e.getMessage() + "). Retrying in 2s...");
                }
                try {
                    Thread.sleep(2000);
                }catch (InterruptedException e){}

            }

            System.out.println("Connected to scanner!");
            try {
                scanner.setDeviceEnabled(true);
                scanner.setDataEventEnabled(true);
            }catch(JposException e){
                e.printStackTrace();
            }
        }).start();
    }

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
    public void errorOccurred(ErrorEvent e) {

    }

    @Override
    public void dataOccurred(DataEvent e) {
        try {
            Scanner scn = (Scanner) e.getSource();
            String data = new String(scn.getScanData());
            System.out.println(data);
            buffer = data;
            send = true;
            scn.setDataEventEnabled(true);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void statusUpdateOccurred(StatusUpdateEvent e) {

    }
}

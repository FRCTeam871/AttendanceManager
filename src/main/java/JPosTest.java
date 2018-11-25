import jpos.JposException;
import jpos.Scanner;
import jpos.events.*;
import jpos.util.JposProperties;
import jpos.util.JposPropertiesConst;
import sensing.JPOSSense;

public class JPosTest implements ErrorListener, DataListener, StatusUpdateListener {

    Scanner scanner;

    public static void main(String[] args){
        System.out.println("Looking for jpos.xml at " + System.getProperty("user.home") + "\\jpos.xml");
        System.setProperty(JposPropertiesConst.JPOS_POPULATOR_FILE_PROP_NAME, System.getProperty("user.home") + "\\jpos.xml");
        new JPosTest();
    }

    public JPosTest(){
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
    public void errorOccurred(ErrorEvent errorEvent) {
        System.out.println("errorOccurred");
    }

    @Override
    public void dataOccurred(DataEvent de) {
        System.out.println("dataOccurred");
        try {
            Scanner scn = (Scanner) de.getSource();
            System.out.println(new String(scn.getScanData()));
            scn.setDataEventEnabled(true);
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public void statusUpdateOccurred(StatusUpdateEvent se) {
        System.out.println("statusUpdateOccurred " + se.toString());
    }
}

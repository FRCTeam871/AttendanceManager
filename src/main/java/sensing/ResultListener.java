package sensing;

public interface ResultListener {

    void changed(BarcodeResult result);
    void scanned(BarcodeResult result);

}

package com.team871.sensing;

public interface ResultListener {

    void changed(BarcodeResult result);
    void scanned(BarcodeResult result);

}

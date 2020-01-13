package com.team871.sensing;

public interface ScannerListener {
    void onScanned(BarcodeResult result, boolean changed);
}

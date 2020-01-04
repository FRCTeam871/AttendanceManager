package com.team871.sensing;

import com.team871.ui.TickListener;

import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class AbstractBarcodeReader implements TickListener {
    private BarcodeResult cachedResult;
    private final List<ScannerListener> listeners = new ArrayList<>();
    private final Deque<BarcodeResult> dataQueue = new ArrayDeque<>();

    protected void enqueueResult(String data) {
        dataQueue.offer(new BarcodeResult(data, System.currentTimeMillis()));
    }

    protected void fireScannedEvent(BarcodeResult event, boolean changed) {
        listeners.forEach(l -> l.onScanned(event, changed));
    }

    public BarcodeResult getLastResult() {
        return cachedResult;
    }

    protected BarcodeResult getNextResult() {
        return dataQueue.poll();
    }

    public void addListener(ScannerListener listener) {
        listeners.add(listener);
    }

    public void resetLast() {
        cachedResult = null;
    }

    public abstract Collection<? extends String> getDebugInfo();
    public abstract void render(Graphics2D g, int width, int height);
    public abstract void shutdown();
}

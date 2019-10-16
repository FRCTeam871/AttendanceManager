package com.team871.sensing;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractBarcodeReader {
    private BarcodeResult cachedResult;
    private final List<ResultListener> listeners = new ArrayList<>();

    public void update() {
        final BarcodeResult newResult = findResult();
        if (newResult == null) {
            return;
        }

        final BarcodeResult oldResult = cachedResult;
        cachedResult = newResult;

        if (oldResult == null || !newResult.getText().equals(oldResult.getText())) {
            listeners.forEach(l -> l.changed(newResult));
        }

        listeners.forEach(l -> l.scanned(newResult));
    }

    public BarcodeResult getCachedResult() {
        return cachedResult;
    }

    public void addListener(ResultListener listener) {
        listeners.add(listener);
    }

    public void resetCache() {
        cachedResult = null;
    }

    protected abstract BarcodeResult findResult();

    public abstract Collection<? extends String> getDebugInfo();

    public abstract void renderPreview(Graphics2D g, int width, int height);
}

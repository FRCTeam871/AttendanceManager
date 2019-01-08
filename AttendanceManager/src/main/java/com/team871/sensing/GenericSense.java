package com.team871.sensing;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class GenericSense {

    BarcodeResult cachedResult;
    BarcodeResult instantResult;
    List<ResultListener> listeners;

    public GenericSense() {
        this.listeners = new ArrayList<>();
    }

    public void update(){
        instantResult = null;

        BarcodeResult newResult = findResult();
        if(newResult == null) return;

        BarcodeResult oldResult = cachedResult;

        cachedResult = newResult;

        if(oldResult == null || !newResult.getText().equals(oldResult.getText())){
            for(ResultListener l : listeners) l.changed(newResult);
        }

        for(ResultListener l : listeners) l.scanned(newResult);

        instantResult = cachedResult;
    }

    protected abstract BarcodeResult findResult();

    public BarcodeResult getInstantResult(){
        return instantResult;
    }

    public BarcodeResult getCachedResult(){
        return cachedResult;
    }

    public boolean addListener(ResultListener listener){
        return listeners.add(listener);
    }

    public boolean removeListener(ResultListener listener){
        return listeners.remove(listener);
    }

    public abstract void renderPreview(Graphics2D g, int width, int height);

    public void resetCache(){
        cachedResult = null;
    }

    public abstract Collection<? extends String> getDebugInfo();
}
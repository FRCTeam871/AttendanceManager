package com.team871.sensing;

public class BarcodeResult {

    private final String text;
    private final long time;

    public BarcodeResult(String text, long time){
        this.text = text;
        this.time = time;
    }

    public String getText(){
        return text;
    }

    public long getTime(){
        return time;
    }

}

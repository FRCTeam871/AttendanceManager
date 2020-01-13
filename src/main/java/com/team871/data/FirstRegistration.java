package com.team871.data;

public enum FirstRegistration {
    None("Not Signed"),
    MissingWaiver("Missing Waiver"),
    Complete("Done");

    final String key;

    FirstRegistration(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static FirstRegistration getByKey(String key) {
        for(FirstRegistration r : values()) {
            if(r.key.equals(key)) {
                return r;
            }
        }

        throw new IllegalArgumentException("No such key " + key);
    }
}

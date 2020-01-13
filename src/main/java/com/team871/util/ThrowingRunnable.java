package com.team871.util;

@FunctionalInterface
public interface ThrowingRunnable<P, E extends Exception> {
    void run(P param) throws E;
}

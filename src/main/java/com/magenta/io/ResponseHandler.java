package com.magenta.io;

public interface ResponseHandler {
    void write(String token);
    void complete();
    void error(Throwable t);
    String getBuffer();
}

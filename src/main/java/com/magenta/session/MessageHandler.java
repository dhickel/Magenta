package com.magenta.session;


@FunctionalInterface
public interface MessageHandler<T extends Session> {

    void processMessage(T session, String message);
}

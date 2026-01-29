package com.magenta.io;

import com.magenta.security.SecurityFilter;


public interface IOManager extends InputPipe, OutputPipe, AutoCloseable {

    SecurityFilter securityFilter();


    void setSecurityFilter(SecurityFilter filter);


    void setCursor(String cursor, Integer cursorColor);


    ResponseHandler createResponseHandler(Integer agentColor, int delayMs);

    @Override
    default void close() throws Exception {
        // Default no-op, implementations can override
    }
}

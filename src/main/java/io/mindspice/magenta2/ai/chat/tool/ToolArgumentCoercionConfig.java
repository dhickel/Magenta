package io.mindspice.magenta2.ai.chat.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.stereotype.Component;

@Component
public class ToolArgumentCoercionConfig {

    @PostConstruct
    public void enableSingleValueAsArray() {
        JsonParser.getObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }
}

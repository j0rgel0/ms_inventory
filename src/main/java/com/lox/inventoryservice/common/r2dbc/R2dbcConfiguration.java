// src/main/java/com/lox/authservice/config/R2dbcConfiguration.java

package com.lox.inventoryservice.common.r2dbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactory;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;

@Configuration
public class R2dbcConfiguration extends AbstractR2dbcConfiguration {

    @Override
    public ConnectionFactory connectionFactory() {
        return null;
    }

    @Override
    protected List<Object> getCustomConverters() {
        return Arrays.asList(
                new JsonNodeToJsonConverter(new ObjectMapper()),
                new JsonToJsonNodeConverter(new ObjectMapper())
        );
    }
}

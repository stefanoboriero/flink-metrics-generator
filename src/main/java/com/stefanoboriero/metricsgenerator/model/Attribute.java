package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Attribute {
    private final String key;
    private final Value value;

    @JsonCreator
    public Attribute(@JsonProperty("key") String key,
                     @JsonProperty("value") Value value) {
        this.key = key;
        this.value = value;
    }

    String key() {
        return key;
    }

    Value value() {
        return value;
    }
}


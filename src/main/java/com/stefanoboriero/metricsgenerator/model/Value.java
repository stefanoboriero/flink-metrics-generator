package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Value {
    private final String stringValue;
    private final String intValue;

    @JsonCreator
    public Value(@JsonProperty("stringValue") String stringValue, @JsonProperty("intValue") String intValue) {
        this.stringValue = stringValue;
        this.intValue = intValue;
    }

    String stringValue() {
        return stringValue;
    }

    String intValue() {
        return intValue;
    }
}

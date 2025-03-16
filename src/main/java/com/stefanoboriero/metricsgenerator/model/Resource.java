package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

public class Resource {
    private final List<Attribute> attributes;

    @JsonCreator
    public Resource(@JsonProperty("attributes") List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Optional<String> getResourceName() {
        return attributes.stream()
                .filter(attribute -> "service.name".equals(attribute.key()))
                .map(attribute -> attribute.value().stringValue())
                .findFirst();
    }
}

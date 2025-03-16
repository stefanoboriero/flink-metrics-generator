package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScopeSpan {
    private String serviceName;
    private final List<Span> spans;

    @JsonCreator
    public ScopeSpan(@JsonProperty("spans") List<Span> spans) {
        this.spans = spans;
    }

    public List<Span> spans() {
        return this.spans;
    }

    public String serviceName() {
        return this.serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}

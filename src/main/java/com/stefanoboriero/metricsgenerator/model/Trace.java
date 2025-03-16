package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ListResourceBundle;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Trace {
    private final List<ResourceSpan> resourceSpans;

    @JsonCreator
    public Trace(@JsonProperty("resourceSpans") List<ResourceSpan> resourceSpans) {
        this.resourceSpans = resourceSpans;
    }

    public List<ResourceSpan> resourceSpans() {
        return this.resourceSpans;
    }
}

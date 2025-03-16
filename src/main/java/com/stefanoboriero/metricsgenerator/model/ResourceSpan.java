package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceSpan {
    private final Resource resource;
    private final List<ScopeSpan> scopeSpans;

    @JsonCreator
    public ResourceSpan(@JsonProperty("resource") Resource resource,
                        @JsonProperty("scopeSpans") List<ScopeSpan> scopeSpans) {
        this.resource = resource;
        this.scopeSpans = scopeSpans;
    }

    public Resource resource() {
        return this.resource;
    }

    public List<ScopeSpan> scopeSpans() {
        return this.scopeSpans;
    }
}

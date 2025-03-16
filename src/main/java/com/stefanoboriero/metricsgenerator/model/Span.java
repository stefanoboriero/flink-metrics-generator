package com.stefanoboriero.metricsgenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Span {
    private final Long startTimeUnixNano;
    private final Long endTimeUnixNano;
    private final List<Attribute> attributes;
    private final Integer kind;

    public String serviceName; // TODO see if this should be get from the keyed stream context, and not propagated down to this point

    @JsonCreator
    public Span(@JsonProperty("startTimeUnixNano") Long startTimeUnixNano,
                @JsonProperty("endTimeUnixNano") Long endTimeUnixNano,
                @JsonProperty("attributes") List<Attribute> attributes,
                @JsonProperty("kind") Integer kind
    ) {
        this.startTimeUnixNano = startTimeUnixNano;
        this.endTimeUnixNano = endTimeUnixNano;
        this.attributes = attributes;
        this.kind = kind;
    }

    public Integer kind() {
        return kind;
    }

    public Long getDurationNanoSeconds() {
        return endTimeUnixNano - startTimeUnixNano;
    }

    public Optional<String> getStatusCode() {
        return attributes.stream()
                .filter(attribute -> "http.response.status_code".equals(attribute.key()))
                .map(attribute -> attribute.value().intValue())
                .findFirst();
    }
}

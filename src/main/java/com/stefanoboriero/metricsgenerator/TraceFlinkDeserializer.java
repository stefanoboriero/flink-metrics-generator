package com.stefanoboriero.metricsgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stefanoboriero.metricsgenerator.model.Trace;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

public class TraceFlinkDeserializer implements DeserializationSchema<Trace> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Trace deserialize(byte[] bytes) throws IOException {
            return objectMapper.readValue(bytes, Trace.class);
    }

    @Override
    public boolean isEndOfStream(Trace trace) {
        return false;
    }

    @Override
    public TypeInformation<Trace> getProducedType() {
        return TypeInformation.of(Trace.class);
    }
}

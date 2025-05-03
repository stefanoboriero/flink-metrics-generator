/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stefanoboriero.metricsgenerator;

import com.stefanoboriero.metricsgenerator.model.ResourceSpan;
import com.stefanoboriero.metricsgenerator.model.ScopeSpan;
import com.stefanoboriero.metricsgenerator.model.Span;
import com.stefanoboriero.metricsgenerator.model.Trace;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.connector.base.sink.AsyncSinkBase;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.prometheus.sink.PrometheusSink;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeries;
import org.apache.flink.connector.prometheus.sink.prometheus.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Skeleton for a Flink DataStream Job.
 *
 * <p>For a tutorial how to write a Flink application, check the
 * tutorials and examples on the <a href="https://flink.apache.org">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class MetricsGenerator {

    public static void main(String[] args) throws Exception {
        // Sets up the execution environment, which is the main entry point
        // to building Flink applications.
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        AsyncSinkBase<PrometheusTimeSeries, Types.TimeSeries> prometheusSink = PrometheusSink.builder()
                .setPrometheusRemoteWriteUrl("http://mimir:9090/api/v1/push")
                .build();

        KafkaSource<Trace> source = KafkaSource.<Trace>builder()
                .setBootstrapServers("kafka:19092")
                .setTopics("traces")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TraceFlinkDeserializer())
                .build();

        DataStream<Trace> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
        DataStream<HttpServerMetricsSample> counter = stream
                .flatMap(new ResourceSpanFlattener())
                .flatMap(new ScopeSpanFlattener())
                .flatMap(new SpanFlattener())
                .filter((span) -> span.kind() == 2) // Filter only spans of kind SERVER
                .keyBy(CompositeKey::new)
                .window(TumblingProcessingTimeWindows.of(Duration.ofSeconds(15)))
                .aggregate(new AggregatingCounter(), new MyProcessWindowFunction());

        DataStream<PrometheusTimeSeries> timeSeriesDataStream = counter.map(new PrometheusSampleMapper())
                .flatMap(new TimeseriesFlattener());
        timeSeriesDataStream.sinkTo(prometheusSink);
        // Execute program, beginning computation.
        env.execute("RED metrics generator");
    }


    static class CompositeKey {
        private final String serviceName;
        private final Integer spanKind;
        private final String statusCode;

        public CompositeKey(Span span) {
            this.serviceName = span.serviceName;
            this.spanKind = span.kind();
            this.statusCode = span.getStatusCode().orElse("");
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            CompositeKey that = (CompositeKey) o;
            return Objects.equals(serviceName, that.serviceName)
                    && Objects.equals(statusCode, that.statusCode)
                    && Objects.equals(spanKind, that.spanKind);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceName, spanKind, statusCode);
        }
    }

    static class HttpServerMetricsSample {
        private final Integer spanKind;
        private final String serviceName;
        private final String httpStatusCode;
        private final Double count;
        private final Double totalLatencySeconds;

        HttpServerMetricsSample(String serviceName, Integer spanKind, String httpStatusCode, Double count, Double latency) {
            this.serviceName = serviceName;
            this.httpStatusCode = httpStatusCode;
            this.spanKind = spanKind;
            this.count = count;
            this.totalLatencySeconds = latency;
        }

        String getSpanKindString() {
            return switch (spanKind) {
                case 1 -> "INTERNAL";
                case 2 -> "SERVER";
                case 3 -> "CLIENT";
                case 4 -> "PRODUCER";
                case 5 -> "CONSUMER";
                default -> "UNSPECIFIED";
            };
        }
    }

    static class HttpServerMetricsAccumulator {
        private Integer count;
        private Long totalLatencyNanoSeconds;

        HttpServerMetricsAccumulator(Integer count, Long latency) {
            this.count = count;
            this.totalLatencyNanoSeconds = latency;
        }

        public Integer count() {
            return count;
        }

        public Long totalLatencyNanoSeconds() {
            return totalLatencyNanoSeconds;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public void setTotalLatencyNanoSeconds(Long totalLatencyNanoSeconds) {
            this.totalLatencyNanoSeconds = totalLatencyNanoSeconds;
        }
    }

    private static class PrometheusSampleMapper implements MapFunction<HttpServerMetricsSample, List<PrometheusTimeSeries>> {
        @Override
        public List<PrometheusTimeSeries> map(HttpServerMetricsSample httpServerMetricSample) {
            List<PrometheusTimeSeries> timeSeries = new ArrayList<>(2);
            long timestamp = System.currentTimeMillis();
            var requestCountTimeseries = PrometheusTimeSeries.builder()
                    .withMetricName("flink_http_server_requests_seconds_count")
                    .addLabel("spanKind", httpServerMetricSample.getSpanKindString())
                    .addLabel("serviceName", httpServerMetricSample.serviceName)
                    .addLabel("status", httpServerMetricSample.httpStatusCode)
                    .addSample(httpServerMetricSample.count, timestamp)
                    .build();
            var requestLatencyTimeseries = PrometheusTimeSeries.builder()
                    .withMetricName("flink_http_server_requests_seconds_sum")
                    .addLabel("spanKind", httpServerMetricSample.getSpanKindString())
                    .addLabel("serviceName", httpServerMetricSample.serviceName)
                    .addLabel("status", httpServerMetricSample.httpStatusCode)
                    .addSample(httpServerMetricSample.totalLatencySeconds, timestamp)
                    .build();
            timeSeries.add(requestCountTimeseries);
            timeSeries.add(requestLatencyTimeseries);
            return timeSeries;
        }
    }

    private static class AggregatingCounter implements AggregateFunction<Span, HttpServerMetricsAccumulator, HttpServerMetricsAccumulator> {
        @Override
        public HttpServerMetricsAccumulator createAccumulator() {
            return new HttpServerMetricsAccumulator( 0, 0L);
        }

        @Override
        public HttpServerMetricsAccumulator add(Span span, HttpServerMetricsAccumulator accumulator) {
            accumulator.setCount(accumulator.count() + 1);
            accumulator.setTotalLatencyNanoSeconds(accumulator.totalLatencyNanoSeconds() + span.getDurationNanoSeconds());
            return accumulator;
        }

        @Override
        public HttpServerMetricsAccumulator getResult(HttpServerMetricsAccumulator accumulator) {
            return accumulator;
        }

        @Override
        public HttpServerMetricsAccumulator merge(HttpServerMetricsAccumulator acc1, HttpServerMetricsAccumulator acc2) {
            acc1.setCount(acc1.count() + acc2.count());
            acc1.setTotalLatencyNanoSeconds(acc1.totalLatencyNanoSeconds() + acc2.totalLatencyNanoSeconds());
            return acc1;
        }
    }

    private static class MyProcessWindowFunction
            extends ProcessWindowFunction<HttpServerMetricsAccumulator, HttpServerMetricsSample, CompositeKey, TimeWindow> {

        @Override
        public void process(CompositeKey compositeKey,
                            ProcessWindowFunction<HttpServerMetricsAccumulator, HttpServerMetricsSample, CompositeKey, TimeWindow>.Context context,
                            Iterable<HttpServerMetricsAccumulator> iterable,
                            Collector<HttpServerMetricsSample> collector) {
            HttpServerMetricsAccumulator accumulator = iterable.iterator().next();
            HttpServerMetricsSample sample = new HttpServerMetricsSample(compositeKey.serviceName,
                    compositeKey.spanKind,
                    compositeKey.statusCode,
                    accumulator.count.doubleValue(),
                    accumulator.totalLatencyNanoSeconds() / 1.0e9);
            collector.collect(sample);
        }
    }

    private static class TimeseriesFlattener implements FlatMapFunction<List<PrometheusTimeSeries>, PrometheusTimeSeries> {
        @Override
        public void flatMap(List<PrometheusTimeSeries> series, Collector<PrometheusTimeSeries> collector) {
            series.forEach(collector::collect);
        }
    }

    private static class ResourceSpanFlattener implements FlatMapFunction<Trace, ResourceSpan> {
        @Override
        public void flatMap(Trace trace, Collector<ResourceSpan> collector) {
            trace.resourceSpans().forEach(collector::collect);
        }
    }

    private static class ScopeSpanFlattener implements FlatMapFunction<ResourceSpan, ScopeSpan> {
        @Override
        public void flatMap(ResourceSpan resourceSpan, Collector<ScopeSpan> collector) {
            for (ScopeSpan sc : resourceSpan.scopeSpans()) {
                sc.setServiceName(resourceSpan.resource().getResourceName().orElse("UNKNOWN"));
                collector.collect(sc);
            }
        }
    }

    private static class SpanFlattener implements FlatMapFunction<ScopeSpan, Span> {
        @Override
        public void flatMap(ScopeSpan scopeSpan, Collector<Span> collector) {
            for (Span s : scopeSpan.spans()) {
                s.serviceName = scopeSpan.serviceName();
                collector.collect(s);
            }
        }
    }
}

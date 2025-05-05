# Flink RED Metrics generator

On February 2025 I attended a [talk at FOSDEM](https://fosdem.org/2025/schedule/event/fosdem-2025-5726-apache-flink-and-prometheus-better-together-to-improve-the-efficiency-of-your-observability-platform-at-scale/)
that presented the [Flink connector for Prometheus](https://github.com/apache/flink-connector-prometheus). After hearing it, it got me thinking: could we use this to replace OpenTelemetry collector connectors that
compute aggregated metrics from traces like the [servicegraph connector](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/connector/servicegraphconnector/README.md) or the
[spanmetrics connector](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/connector/spanmetricsconnector/README.md)?
You can take a look at images before and after under the /img folder to get an overview of the idea.

This POC project tries to answer that question :)

## Running

The following command will package the jar artefact, and start up the docker compose

```bash
make
```

The docker-compose sets up few services that interact toghether to provide a funcional local environment similar to what
we would expect in production. There's going to be:

- 1 Kafka instance
- 1 OpenTelemetry collector, configured to receive traces and forward them to Kafka
- 1 Flink cluster, configure to run only the standalone job to compute metrics
- 1 Mimir instance to store the metrics produces
- 1 Grafana instance to display the metrics

## Sending data

We can use the OpenTelemetry collector [telemetrygen](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/cmd/telemetrygen) tool to generate sample OTEL data.

```bash
telemetrygen traces --otlp-insecure --otlp-http --traces 1
```

Once some data has been generated, you can open the locally running Grafana at http://localhost:3000 and see the metrics
The Flink job will generate 2 metrics: flink_http_server_requests_seconds_count and flink_http_server_requests_seconds_sum'

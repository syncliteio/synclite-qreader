# SyncLite QReader – Rapid IoT Data Connector

> Part of the [SyncLite Platform](https://github.com/syncliteio/SyncLite) – Build Anything, Sync Anywhere.

## What is SyncLite QReader?

**SyncLite QReader** is a lightweight IoT data connector that bridges MQTT message brokers and other message queues with any database, data warehouse, or data lake supported by [SyncLite Consolidator](https://github.com/syncliteio/synclite-consolidator). It subscribes to one or more MQTT topics, parses incoming payloads (JSON, CSV, raw bytes), maps them to relational tables, and feeds the data into the SyncLite pipeline for real-time consolidation.

With QReader you can connect thousands of IoT gateways and sensor feeds to a central analytics database in minutes, without writing any custom integration code.

```
IoT Devices / Sensors
       |  MQTT publish
       ▼
  MQTT Broker(s)  --subscribe-->  SyncLite QReader  -->  Staging Storage  -->  SyncLite Consolidator  -->  Destination DB / DW
```

## Key Features

- **Standard MQTT over Eclipse Paho** – one implementation, one protocol (MQTT v3.1 via Eclipse Paho MQTT client); works with any MQTT-compliant broker out of the box
- **One broker per job, unlimited topics** – each QReader job connects to a single broker URL and subscribes to all topics on it via a wildcard (`#`); run parallel jobs for multiple brokers
- **CSV payload parsing** – parses comma-separated (or custom-delimited) message payloads; JSON and Protobuf formats are defined in the codebase but not yet implemented
- **Schema mapping** – maps MQTT topic paths and CSV payload fields to destination table columns
- **QoS levels** – supports QoS 0, 1, and 2
- **TLS/SSL** – secure connections to brokers
- **Auto-reconnect** – survives transient broker outages with configurable retry interval
- **Web UI** – browser-based job configuration, live message rate gauges, and error logs
- **Edge, fog, and cloud analytics** – enables real-time analytics at all three tiers

## Supported Brokers

QReader uses the **Eclipse Paho MQTT v3 client** – there is no per-broker adapter code. Any broker that speaks standard MQTT v3.1 works automatically. The table below lists commonly tested examples:

| Broker | Notes |
|---|---|
| Eclipse Mosquitto | Popular open-source MQTT broker |
| EMQX | High-performance, scalable MQTT platform |
| HiveMQ | Enterprise MQTT broker |
| AWS IoT Core | Managed IoT broker (MQTT over TLS, port 8883) |
| Azure IoT Hub | Microsoft managed IoT broker (MQTT over TLS, port 8883) |
| Any MQTT v3.1-compliant broker | Same code path – no broker-specific integration |

## Quick Start

1. Deploy the SyncLite platform (see [platform README](https://github.com/syncliteio/SyncLite/blob/main/README.md)).
2. Open http://localhost:8080/synclite-qreader
3. Open http://localhost:8080/synclite-consolidator and configure a destination
4. In QReader, click **Configure Job**: enter broker address, topic subscriptions, and field-to-column mappings.
5. Start the job and watch IoT data flow into your destination database in real time.

## Use Cases

- Industrial IoT: machine sensor data â†’ PostgreSQL / ClickHouse analytics
- Smart building: environmental sensors â†’ time-series database
- Fleet tracking: GPS/telemetry â†’ data warehouse
- Energy monitoring: smart meter readings â†’ data lake

## Build

```bash
cd synclite-qreader/root
mvn -Drevision=oss clean install
```

Built WAR: `root/web/target/synclite-qreader-oss.war`

## Related Components

| Component | Role |
|---|---|
| [SyncLite Consolidator](https://github.com/syncliteio/synclite-consolidator) | Receives and delivers IoT data to destinations |
| [SyncLite Job Monitor](https://github.com/syncliteio/synclite-job-monitor) | Monitors and schedules QReader jobs |

## Documentation & Community

- Full documentation: https://github.com/syncliteio/SyncLite/blob/main/DOCUMENTATION.md
- IoT data connector solution: https://www.synclite.io/solutions/iot-data-connector
- Website: https://www.synclite.io
- Community: https://github.com/syncliteio/SyncLite/issues

---

â† Back to the [SyncLite Platform README](https://github.com/syncliteio/SyncLite/blob/main/README.md)


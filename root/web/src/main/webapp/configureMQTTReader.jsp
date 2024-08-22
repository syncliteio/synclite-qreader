<%-- 
    Copyright (c) 2024 mahendra.chavan@syncLite.io, all rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
    in compliance with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License
    is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
    or implied.  See the License for the specific language governing permissions and limitations
    under the License.
--%>

<%@page import="java.nio.file.Files"%>
<%@page import="java.time.Instant"%>
<%@page import="java.io.File"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.io.BufferedReader"%>
<%@page import="java.io.FileReader"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.HashMap"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ page import="java.sql.*"%>
<%@ page import="org.sqlite.*"%>
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href=css/SyncLiteStyle.css>

<title>Configure QReader Job</title>
</head>


<%
String errorMsg = request.getParameter("errorMsg");
HashMap properties = new HashMap<String, String>();

if (session.getAttribute("job-name") != null) {
	properties.put("job-name", session.getAttribute("job-name").toString());
} else {
	response.sendRedirect("syncLiteTerms.jsp");
}

if (session.getAttribute("synclite-device-dir") != null) {
	properties.put("synclite-device-dir", session.getAttribute("synclite-device-dir").toString());
} else {
	response.sendRedirect("syncLiteTerms.jsp");
}

if (request.getParameter("synclite-logger-configuration-file") != null) {
	properties.put("synclite-logger-configuration-file", request.getParameter("synclite-logger-configuration-file"));
} else {
	Path defaultLoggerConfPath = Path.of(properties.get("synclite-device-dir").toString(), "synclite_logger.conf");
	properties.put("synclite-logger-configuration-file", defaultLoggerConfPath);	
}

if (request.getParameter("mqtt-broker-url") != null) {
	properties.put("mqtt-broker-url", request.getParameter("mqtt-broker-url"));
} else {
	properties.put("mqtt-broker-url", "tcp://localhost:1883");
}

if (request.getParameter("mqtt-broker-user") != null) {
	properties.put("mqtt-broker-user", request.getParameter("mqtt-broker-user"));
} else {
	properties.put("mqtt-broker-user", "");
}

if (request.getParameter("mqtt-broker-password") != null) {
	properties.put("mqtt-broker-password", request.getParameter("mqtt-broker-password"));
} else {
	properties.put("mqtt-broker-password", "");
}

if (request.getParameter("mqtt-broker-connection-timeout-s") != null) {
	properties.put("mqtt-broker-connection-timeout-s", request.getParameter("mqtt-broker-connection-timeout-s"));
} else {
	properties.put("mqtt-broker-connection-timeout-s", "30");	
}

if (request.getParameter("mqtt-broker-connection-retry-interval-s") != null) {
	properties.put("mqtt-broker-connection-retry-interval-s", request.getParameter("mqtt-broker-connection-retry-interval-s"));
} else {
	properties.put("mqtt-broker-connection-retry-interval-s", "5");	
}

if (request.getParameter("mqtt-message-header-delimiter") != null) {
	properties.put("mqtt-message-header-delimiter", request.getParameter("mqtt-message-header-delimiter"));
} else {
	properties.put("mqtt-message-header-delimiter", "/");
}

if (request.getParameter("src-message-format") != null) {
	properties.put("src-message-format", request.getParameter("src-message-format"));
} else {
	properties.put("src-message-format", "CSV");
}

if (request.getParameter("src-message-field-delimiter") != null) {
	properties.put("src-message-field-delimiter", request.getParameter("src-message-field-delimiter"));
} else {
	properties.put("src-message-field-delimiter", ",");
}

if (request.getParameter("mqtt-qos-level") != null) {
	properties.put("mqtt-qos-level", request.getParameter("mqtt-qos-level"));
} else {
	properties.put("mqtt-qos-level", "0");
}

if (request.getParameter("mqtt-clean-session") != null) {
	properties.put("mqtt-clean-session", request.getParameter("mqtt-clean-session"));
} else {
	properties.put("mqtt-clean-session", "false");
}

if (request.getParameter("qreader-message-batch-processing") != null) {
	properties.put("qreader-message-batch-processing", request.getParameter("qreader-message-batch-processing"));
} else {
	properties.put("qreader-message-batch-processing", "true");
}

if (request.getParameter("qreader-message-batch-flush-interval-ms") != null) {
	properties.put("qreader-message-batch-flush-interval-ms", request.getParameter("qreader-message-batch-flush-interval-ms"));
} else {
	properties.put("qreader-message-batch-flush-interval-ms", "5000");	
}

if (request.getParameter("src-num-topics") != null) {
	properties.put("src-num-topics", request.getParameter("src-num-topics"));
} else {
	properties.put("src-num-topics", "1");	
}


if (request.getParameter("qreader-trace-level") != null) {
	properties.put("qreader-trace-level", request.getParameter("qreader-trace-level"));
} else {
	properties.put("qreader-trace-level", "INFO");
}


if (request.getParameter("qreader-synclite-device-type") != null) {
	properties.put("qreader-synclite-device-type", request.getParameter("qreader-synclite-device-type"));
} else {
	properties.put("qreader-synclite-device-type", "TELEMETRY");
}

if (request.getParameter("qreader-map-devices-to-single-synclite-device") != null) {
	properties.put("qreader-map-devices-to-single-synclite-device", request.getParameter("qreader-map-devices-to-single-synclite-device"));
} else {
	properties.put("qreader-map-devices-to-single-synclite-device", "TELEMETRY");
}

if (request.getParameter("qreader-default-synclite-device-name") != null) {
	properties.put("qreader-default-synclite-device-name", request.getParameter("qreader-default-synclite-device-name"));
} else {
	properties.put("qreader-default-synclite-device-name", "default");
}

if (request.getParameter("qreader-ignore-messages-for-undefined-topics") != null) {
	properties.put("qreader-ignore-messages-for-undefined-topics", request.getParameter("qreader-ignore-messages-for-undefined-topics"));
} else {
	properties.put("qreader-ignore-messages-for-undefined-topics", "false");
}

if (request.getParameter("qreader-default-synclite-table-name") != null) {
	properties.put("qreader-default-synclite-table-name", request.getParameter("qreader-default-synclite-table-name"));
} else {
	properties.put("qreader-default-synclite-table-name", "default_table");
}

if (request.getParameter("qreader-ignore-corrupt-messages") != null) {
	properties.put("qreader-ignore-corrupt-messages", request.getParameter("qreader-ignore-corrupt-messages"));
} else {
	properties.put("qreader-ignore-corrupt-messages", "false");
}

if (request.getParameter("qreader-corrupt-messages-synclite-table-name") != null) {
	properties.put("qreader-corrupt-messages-synclite-table-name", request.getParameter("qreader-corrupt-messages-synclite-table-name"));
} else {
	properties.put("qreader-corrupt-messages-synclite-table-name", "corrupt_messages");
}

if (request.getParameter("jvm-arguments") != null) {
	properties.put("jvm-arguments", request.getParameter("jvm-arguments"));
} else {
	properties.put("jvm-arguments", "");
}

if (request.getParameter("qreader-message-batch-processing") == null) {
	//Read configs from conf file if they are present
	Path propsPath = Path.of(properties.get("synclite-device-dir").toString(), "synclite_mqttreader.conf");
	BufferedReader reader = null;
	try {
		if (Files.exists(propsPath)) {
			reader = new BufferedReader(new FileReader(propsPath.toFile()));
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (line.trim().isEmpty()) {
					line = reader.readLine();
					continue;
				}
				if (line.startsWith("#")) {
					line = reader.readLine();
					continue;
				}
				String[] tokens = line.split("=");
				if (tokens.length < 2) {
					if (tokens.length == 1) {
						if (!line.startsWith("=")) {
							properties.put(tokens[0].trim().toLowerCase(), line.substring(line.indexOf("=") + 1, line.length()).trim());
						}
					}
				} else {
					properties.put(tokens[0].trim().toLowerCase(), line.substring(line.indexOf("=") + 1, line.length()).trim());
				}
				line = reader.readLine();
			}
			reader.close();
		}
	} catch (Exception e) {
		if (reader != null) {
			reader.close();
		}
		throw e;
	}

}

String conf = "";
if (request.getParameter("synclite-logger-configuration") != null) {
	conf =  request.getParameter("synclite-logger-configuration");
} else if (Files.exists(Path.of(properties.get("synclite-logger-configuration-file").toString()))) { 
	conf = Files.readString(Path.of(properties.get("synclite-logger-configuration-file").toString()));
} else {
	StringBuilder confBuilder = new StringBuilder();
	String newLine = System.getProperty("line.separator");

	String stageDir = Path.of(System.getProperty("user.home"), "synclite", properties.get("job-name").toString(), "stageDir").toString();
	String commandDir = Path.of(System.getProperty("user.home"), "synclite", properties.get("job-name").toString(), "commandDir").toString();

	confBuilder.append("#==============Device Stage Properties==================");
	confBuilder.append(newLine);
	confBuilder.append("local-data-stage-directory=").append(stageDir);
	confBuilder.append(newLine);
	confBuilder.append("#local-data-stage-directory=<path/to/local/data/stage/directory>");
	confBuilder.append(newLine);
	confBuilder.append("local-command-stage-directory=").append(commandDir);
	confBuilder.append(newLine);
	confBuilder.append("#local-command-stage-directory=<path/to/local/command/stage/directory  #specify if device command handler is enabled>");
	confBuilder.append(newLine);
	confBuilder.append("destination-type=FS");
	confBuilder.append(newLine);
	confBuilder.append("#destination-type=<FS|MS_ONEDRIVE|GOOGLE_DRIVE|SFTP|MINIO|KAFKA|S3>");
	confBuilder.append(newLine);
	confBuilder.append(newLine);
	confBuilder.append("#==============SFTP Configuration=================");
	confBuilder.append(newLine);		
	confBuilder.append("#sftp:host=<host name of SFTP server to receive shipped devices and device logs>");
	confBuilder.append(newLine);
	confBuilder.append("#sftp:port=<port number of SFTP server>");
	confBuilder.append(newLine);
	confBuilder.append("#sftp:user-name=<user name to connect to remote host>");
	confBuilder.append(newLine);
	confBuilder.append("#sftp:password=<password>");
	confBuilder.append(newLine);
	confBuilder.append("#sftp:remote-data-stage-directory=<remote data stage directory name that will stage device directories>");
	confBuilder.append(newLine);
	confBuilder.append("#sftp:remote-command-stage-directory=<remote command directory name which will hold command files sent by consolidator if device command handler is enabled>");
	confBuilder.append(newLine);
	confBuilder.append(newLine);	
	confBuilder.append("#==============MinIO  Configuration=================");
	confBuilder.append(newLine);
	confBuilder.append("#minio:endpoint=<MinIO endpoint to upload devices>");
	confBuilder.append(newLine);
	confBuilder.append("#minio:access-key=<MinIO access key>");
	confBuilder.append(newLine);
	confBuilder.append("#minio:secret-key=<MinIO secret key>");
	confBuilder.append(newLine);
	confBuilder.append("#minio:data-stage-bucket-name=<MinIO data stage bucket name that will host device directories>");
	confBuilder.append(newLine);
	confBuilder.append("#minio:command-stage-bucket-name=<MinIO command stage bucket name that will hold command files sent by SyncLite Consolidator>");
	confBuilder.append(newLine);
	confBuilder.append(newLine);	
	confBuilder.append("#==============S3 Configuration=====================");
	confBuilder.append(newLine);
	confBuilder.append("#s3:endpoint=https://s3-<region>.amazonaws.com");
	confBuilder.append(newLine);
	confBuilder.append("#s3:access-key=<S3 access key>");
	confBuilder.append(newLine);
	confBuilder.append("#s3:secret-key=<S3 secret key>");
	confBuilder.append(newLine);
	confBuilder.append("#s3:data-stage-bucket-name=<S3 data stage bucket name that will hold device directories>");
	confBuilder.append(newLine);
	confBuilder.append("#s3:command-stage-bucket-name=<S3 command stage bucket name that will hold command files sent by SyncLite Consolidator>");
	confBuilder.append(newLine);
	confBuilder.append(newLine);
	confBuilder.append("#==============Kafka Configuration=================");
	confBuilder.append(newLine);
	confBuilder.append("#kafka-producer:bootstrap.servers=localhost:9092,localhost:9093,localhost:9094");
	confBuilder.append(newLine);
	confBuilder.append("#kafka-producer:<any_other_kafka_producer_property> = <kafka_producer_property_value>");
	confBuilder.append(newLine);
	confBuilder.append("#kafka-producer:<any_other_kafka_producer_property> = <kafka_producer_property_value>");
	confBuilder.append(newLine);
	confBuilder.append("#kafka-consumer:bootstrap.servers=localhost:9092,localhost:9093,localhost:9094");
	confBuilder.append(newLine);
	confBuilder.append("#kafka-consumer:<any_other_kafka_consumer_property> = <kafka_consumer_property_value>");
	confBuilder.append(newLine);
	confBuilder.append("#kafka-consumer:<any_other_kafka_consumer_property> = <kafka_consumer_property_value>");
	confBuilder.append(newLine);
	confBuilder.append(newLine);
	confBuilder.append("#==============Table filtering Configuration=================");
	confBuilder.append(newLine);
	confBuilder.append("#include-tables=<comma separate table list>");
	confBuilder.append(newLine);
	confBuilder.append("#exclude-tables=<comma separate table list>");
	confBuilder.append(newLine);
	confBuilder.append(newLine);
	confBuilder.append("#==============Logger Configuration==================");	
	confBuilder.append(newLine);
	confBuilder.append("#log-queue-size=2147483647");
	confBuilder.append(newLine);
	confBuilder.append("#log-segment-flush-batch-size=1000000");
	confBuilder.append(newLine);
	confBuilder.append("#log-segment-switch-log-count-threshold=1000000");
	confBuilder.append(newLine);
	confBuilder.append("#log-segment-switch-duration-threshold-ms=5000");
	confBuilder.append(newLine);
	confBuilder.append("#log-segment-shipping-frequency-ms=5000");
	confBuilder.append(newLine);
	confBuilder.append("#log-segment-page-size=4096");
	confBuilder.append(newLine);
	confBuilder.append("#log-max-inlined-arg-count=16");
	confBuilder.append(newLine);
	confBuilder.append("#use-precreated-data-backup=false");
	confBuilder.append(newLine);
	confBuilder.append("#vacuum-data-backup=true");
	confBuilder.append(newLine);
	confBuilder.append("#skip-restart-recovery=false");
	confBuilder.append(newLine);
	confBuilder.append(newLine);
	confBuilder.append("#==============Command Handler Configuration==================");
	confBuilder.append(newLine);
	confBuilder.append("#enable-command-handler=false|true");
	confBuilder.append(newLine);
	confBuilder.append("#command-handler-type=INTERNAL|EXTERNAL");
	confBuilder.append(newLine);
	confBuilder.append("#external-command-handler=synclite_command_processor.bat <COMMAND> <COMMAND_FILE>");
	confBuilder.append(newLine);
	confBuilder.append("#external-command-handler=synclite_command_processor.sh <COMMAND> <COMMAND_FILE>");
	confBuilder.append(newLine);
	confBuilder.append("#command-handler-frequency-ms=10000");
	confBuilder.append(newLine);
	confBuilder.append(newLine);
	confBuilder.append("#==============Device Configuration==================");
	confBuilder.append(newLine);
	String deviceEncryptionKeyFile = Path.of(System.getProperty("user.home"), ".ssh", "synclite_public_key.der").toString();
	confBuilder.append("#device-encryption-key-file=" + deviceEncryptionKeyFile);
	confBuilder.append(newLine);
	confBuilder.append("#device-name=");
	confBuilder.append(newLine);	

	conf = confBuilder.toString();
}

properties.put("synclite-logger-configuration", conf);

%>

<body>
	<%@include file="html/menu.html"%>	

	<div class="main">
		<h2>Configure QReader Job</h2>
		<%
		if (errorMsg != null) {
			out.println("<h4 style=\"color: red;\">" + errorMsg + "</h4>");
		}
		%>

		<form action="${pageContext.request.contextPath}/validatemqttreader" method="post">
			<table>
				<tbody>
					<tr>
						<td>SyncLite Device Directory</td>
						<td><input type="text" size=50 id="synclite-device-dir"
							name="synclite-device-dir"
							value="<%=properties.get("synclite-device-dir")%>"
							title="Specify SyncLite device directory" readonly/>
						</td>
					</tr>

					<tr>
						<td>MQTT Broker URL</td>
						<td><input type="text" size=50 id="mqtt-broker-url"
							name="mqtt-broker-url"
							value="<%=properties.get("mqtt-broker-url")%>"
							title="Specify MQTT broker url"/>
						</td>
					</tr>

					<tr>
						<td>MQTT Broker User</td>
						<td><input type="text" id="mqtt-broker-user"
							name="mqtt-broker-user"
							value="<%=properties.get("mqtt-broker-user")%>"
							title="Specify MQTT broker user name"/>
						</td>
					</tr>

					<tr>
						<td>MQTT Broker Password</td>
						<td><input type="password" id="mqtt-broker-password"
							name="mqtt-broker-password"
							value="<%=properties.get("mqtt-broker-password")%>"
							title="Specify MQTT broker user password"/>
						</td>
					</tr>

					<tr>
						<td>Broker Connection Timeout Interval(s)</td>
						<td><input type="number" size=30 id="mqtt-broker-connection-timeout-s"
							name="mqtt-broker-connection-timeout-s"
							value="<%=properties.get("mqtt-broker-connection-timeout-s")%>"
							title="Specify MQTT broker connection timeout in seconds"/>
						</td>
					</tr>

					<tr>
						<td>Broker Connection Retry Interval(s)</td>
						<td><input type="number" size=30 id="mqtt-broker-connection-retry-interval-s"
							name="mqtt-broker-connection-retry-interval-s"
							value="<%=properties.get("mqtt-broker-connection-retry-interval-s")%>"
							title="Specify MQTT broker connection retry interval in seconds"/>
						</td>
					</tr>

					<tr>
						<td>MQTT Quality of Service Level</td>
						<td><select id="mqtt-qos-level" name="mqtt-qos-level" title="Specify MQTT quality of service level.">
								<%
								if (properties.get("mqtt-qos-level").equals("0")) {
									out.println("<option value=\"0\" selected>At most once(0)</option>");
								} else {
									out.println("<option value=\"0\">At most once(0)</option>");
								}

								if (properties.get("mqtt-qos-level").equals("1")) {
									out.println("<option value=\"1\" selected>At least once(1)</option>");
								} else {
									out.println("<option value=\"1\">At least once(1)</option>");
								}

								if (properties.get("mqtt-qos-level").equals("2")) {
									out.println("<option value=\"2\" selected>Exactly once(2)</option>");
								} else {
									out.println("<option value=\"2\">Exactly once(2)</option>");
								}
								%>
						</select></td>
					</tr>					
					
					<tr>
						<td>MQTT Clean Session</td>
						<td><select id="mqtt-clean-session" name="mqtt-clean-session" title="Specify if a clean MQTT session should be started on connection.">
								<%
								if (properties.get("mqtt-clean-session").equals("true")) {
									out.println("<option value=\"true\" selected>true</option>");
								} else {
									out.println("<option value=\"true\">true</option>");
								}

								if (properties.get("mqtt-clean-session").equals("false")) {
									out.println("<option value=\"false\" selected>false</option>");
								} else {
									out.println("<option value=\"false\">false</option>");
								}
								%>
						</select></td>
					</tr>

					<tr>
						<td>Message Format</td>
						<td><select id="src-message-format" name="src-message-format" value="<%=properties.get("src-message-format")%>" title="Specify message format : CSV/JSON">
								<%
								if (properties.get("src-message-format").equals("CSV")) {
									out.println("<option value=\"CSV\" selected>CSV</option>");
								} else {
									out.println("<option value=\"CSV\">CSV</option>");
								}
								if (properties.get("src-message-format").equals("JSON")) {
									out.println("<option value=\"JSON\" selected>JSON</option>");
								} else {
									out.println("<option value=\"JSON\">JSON</option>");
								}
								%>
							</select>
						</td>
					</tr>

					<tr>
						<td>Message Header Delimiter</td>
						<td><input type="text" size=5 id="mqtt-message-header-delimiter"
							name="mqtt-message-header-delimiter"
							value="<%=properties.get("mqtt-message-header-delimiter")%>"
							title="SyncLite MQTT reader expects each message header in a format deviceName/topicName by default. Change the delimiter from / to any other character as needed."/>
						</td>
					</tr>

					<tr>
						<td>Message Field Delimiter</td>
						<td><input type="text" size=5 id="src-message-field-delimiter"
							name="src-message-field-delimiter"
							value="<%=properties.get("src-message-field-delimiter")%>"
							title="Specify message field delimiter. This is relevant when message format is CSV."/>
						</td>
					</tr>

					<tr>
						<td>Message Batch processing</td>
						<td><select id="qreader-message-batch-processing" name="qreader-message-batch-processing" title="Specify if messages should be buffered and published to SyncLite devices in batches. Enabling this option can substantially enhance message consumption rates during periods of high throughput. However, it's important to note that utilizing in-memory buffering and batching of messages may lead to message loss in the event of a tool restart.">
								<%
								if (properties.get("qreader-message-batch-processing").equals("true")) {
									out.println("<option value=\"true\" selected>true</option>");
								} else {
									out.println("<option value=\"true\">true</option>");
								}

								if (properties.get("qreader-message-batch-processing").equals("false")) {
									out.println("<option value=\"false\" selected>false</option>");
								} else {
									out.println("<option value=\"false\">false</option>");
								}
								%>
						</select></td>
					</tr>

					<tr>
						<td>Message Batch Flush Interval (ms)</td>
						<td><input type="number" size=30 id="qreader-message-batch-flush-interval-ms"
							name="qreader-message-batch-flush-interval-ms"
							value="<%=properties.get("qreader-message-batch-flush-interval-ms")%>"
							title="Specify the interval, in milliseconds, at which a buffered batch of messages should be processed."/>
						</td>
					</tr>

					<tr>
						<td>SyncLite Device Type</td>
						<td><select id="qreader-synclite-device-type" name="qreader-synclite-device-type" title="Specify SyncLite device type to create. Specify *_APPENDER if you intend to query the locally created SyncLite device files (i.e. your chosen embedded database type files) with all the incoming data for in-app analytics on the SyncLite DB Reader host itself. If you only intend to stream the data to destination database then specify STREAMING">
								<%
								if (properties.get("qreader-synclite-device-type").equals("TELEMETRY")) {
									out.println("<option value=\"TELEMETRY\" selected>TELEMETRY</option>");
								} else {
									out.println("<option value=\"TELEMETRY\">TELEMETRY</option>");
								}

								if (properties.get("qreader-synclite-device-type").equals("SQLITE_APPENDER")) {
									out.println("<option value=\"SQLITE_APPENDER\" selected>SQLITE_APPENDER</option>");
								} else {
									out.println("<option value=\"SQLITE_APPENDER\">SQLITE_APPENDER</option>");
								}

								if (properties.get("qreader-synclite-device-type").equals("DUCKDB_APPENDER")) {
									out.println("<option value=\"DUCKDB_APPENDER\" selected>DUCKDB_APPENDER</option>");
								} else {
									out.println("<option value=\"DUCKDB_APPENDER\">DUCKDB_APPENDER</option>");
								}

								if (properties.get("qreader-synclite-device-type").equals("DERBY_APPENDER")) {
									out.println("<option value=\"DERBY_APPENDER\" selected>DERBY_APPENDER</option>");
								} else {
									out.println("<option value=\"DERBY_APPENDER\">DERBY_APPENDER</option>");
								}

								if (properties.get("qreader-synclite-device-type").equals("H2_APPENDER")) {
									out.println("<option value=\"H2_APPENDER\" selected>H2_APPENDER</option>");
								} else {
									out.println("<option value=\"H2_APPENDER\">H2_APPENDER</option>");
								}

								if (properties.get("qreader-synclite-device-type").equals("HYPERSQL_APPENDER")) {
									out.println("<option value=\"HYPERSQL_APPENDER\" selected>HYPERSQL_APPENDER</option>");
								} else {
									out.println("<option value=\"HYPERSQL_APPENDER\">HYPERSQL_APPENDER</option>");
								}
								%>
						</select></td>
					</tr>

					<tr>
						<td>Map Devices To Single SyncLite Device</td>
						<td><select id="qreader-map-devices-to-single-synclite-device" name="qreader-map-devices-to-single-synclite-device" title="Specify if all incoming devices should be mapped to a single SyncLite device. Setting this to false will create a SyncLite Device for each IoT device sending messages.">
								<%
								if (properties.get("qreader-map-devices-to-single-synclite-device").equals("true")) {
									out.println("<option value=\"true\" selected>true</option>");
								} else {
									out.println("<option value=\"true\">true</option>");
								}

								if (properties.get("qreader-map-devices-to-single-synclite-device").equals("false")) {
									out.println("<option value=\"false\" selected>false</option>");
								} else {
									out.println("<option value=\"false\">false</option>");
								}
								%>
						</select></td>
					</tr>

					<tr>
						<td>Default SyncLite Device Name</td>
						<td><input type="text" id="qreader-default-synclite-device-name"
							name="qreader-default-synclite-device-name"
							value="<%=properties.get("qreader-default-synclite-device-name")%>"
							title="Specify default SyncLite device name that should receive all incoming messages which have no device name specified in header. Alsom this default device will receive messages from all incoming devcices when Map Devices To Single SyncLite Device option is set to true."/>
						</td>
					</tr>

					<tr>
						<td>Ignore Messages From Undefined Topics</td>
						<td><select id="qreader-ignore-messages-for-undefined-topics" name="qreader-ignore-messages-for-undefined-topics" title="Specify if messages received for undefined/unspecified topics should be ignored">
								<%
								if (properties.get("qreader-ignore-messages-for-undefined-topics").equals("true")) {
									out.println("<option value=\"true\" selected>true</option>");
								} else {
									out.println("<option value=\"true\">true</option>");
								}

								if (properties.get("qreader-ignore-messages-for-undefined-topics").equals("false")) {
									out.println("<option value=\"false\" selected>false</option>");
								} else {
									out.println("<option value=\"false\">false</option>");
								}
								%>
						</select></td>
					</tr>

					<tr>
						<td>Default SyncLite Table Name</td>
						<td><input type="text" id="qreader-default-synclite-table-name"
							name="qreader-default-synclite-table-name"
							value="<%=properties.get("qreader-default-synclite-table-name")%>"
							title="Specify default SyncLite table name which should receive all incoming messages from topics which are not specified/defined by the user on the Configure MQTT Topics page."/>
						</td>
					</tr>

					<tr>
						<td>Ignore Corrupt Messages</td>
						<td><select id="qreader-ignore-corrupt-messages" name="qreader-ignore-corrupt-messages" title="Specify if corrupt/unparsable messages for defined topics should be ignored">
								<%
								if (properties.get("qreader-ignore-corrupt-messages").equals("true")) {
									out.println("<option value=\"true\" selected>true</option>");
								} else {
									out.println("<option value=\"true\">true</option>");
								}

								if (properties.get("qreader-ignore-corrupt-messages").equals("false")) {
									out.println("<option value=\"false\" selected>false</option>");
								} else {
									out.println("<option value=\"false\">false</option>");
								}
								%>
						</select></td>
					</tr>

					<tr>
						<td>SyncLite Table Name For Corrupt Messages</td>
						<td><input type="text" id="qreader-corrupt-messages-synclite-table-name"
							name="qreader-corrupt-messages-synclite-table-name"
							value="<%=properties.get("qreader-corrupt-messages-synclite-table-name")%>"
							title="Specify SyncLite table name that should receive all incoming messages from topics specified/defined by the user but having unparsable payload or payload with different number of fields than defined by user on the Configure MQTT Topics page."/>
						</td>
					</tr>

					<tr>
						<td>SyncLite Logger Configuration File Path</td>
						<td><input type="text" size=50 id="synclite-logger-configuration-file"
							name="synclite-logger-configuration-file"
							value="<%=properties.get("synclite-logger-configuration-file")%>"
							title="Specify SyncLite Logger Configuration File Path"/>
						</td>
					</tr>

					<tr>
						<td>SyncLite Logger Configuration</td>
						<td><textarea name="synclite-logger-configuration" id="synclite-logger-configuration" rows="25" cols="100" title="Specify SyncLite logger configuration. Specified device configurations are written into a .conf file and supplied to initialization of each database/device. Please note the defaults specified for local-stage-directory and destination-type."><%=properties.get("synclite-logger-configuration")%></textarea>
						</td>
					</tr>

					<tr>
						<td>Job Trace Level</td>
						<td><select id="qreader-trace-level" name="qreader-trace-level" title="Specify mqttreader trace level. DEBUG level indicates exhaustive tracing, ERROR level indicates only error reporting and INFO level indicates tracing of important events including errors in the trace files.">
								<%
								if (properties.get("qreader-trace-level").equals("ERROR")) {
									out.println("<option value=\"ERROR\" selected>ERROR</option>");
								} else {
									out.println("<option value=\"ERROR\">ERROR</option>");
								}

								if (properties.get("qreader-trace-level").equals("INFO")) {
									out.println("<option value=\"INFO\" selected>INFO</option>");
								} else {
									out.println("<option value=\"INFO\">INFO</option>");
								}

								if (properties.get("qreader-trace-level").equals("DEBUG")) {
									out.println("<option value=\"DEBUG\" selected>DEBUG</option>");
								} else {
									out.println("<option value=\"DEBUG\">DEBUG</option>");
								}
								%>
						</select></td>
					</tr>					
					
					<tr>
						<td>Job JVM Arguments</td>
						<td><input type="text" id="jvm-arguments"
							name="jvm-arguments"
							value="<%=properties.get("jvm-arguments")%>"
							title ="Specify JVM arguments which should be used while starting the dbreader job. e.g. For setting initial and max heap size as 8GB, you can specify -Xms8g -Xmx8g"/></td>
					</tr>

					<tr>
						<td>Number of Topics</td>
						<td><input type="number" size=30 id="src-num-topics"
							name="src-num-topics"
							value="<%=properties.get("src-num-topics")%>" 
							title="Specify number of topics to receive messages from"/>
						</td>
					</tr>
					
			</table>
			<center>
				<button type="submit" name="next">Next</button>
			</center>			
		</form>
	</div>
</body>
</html>
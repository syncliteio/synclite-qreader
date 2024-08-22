/*
 * Copyright (c) 2024 mahendra.chavan@synclite.io, all rights reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package com.synclite.qreader.web;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Types;

/**
 * Servlet implementation class ValidateMQTTReader
 */
@WebServlet("/validatemqttreader")
public class ValidateMQTTReader extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Logger globalTracer;

	/**
	 * Default constructor. 
	 */
	public ValidateMQTTReader() {
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**	  
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		try {

			Class.forName("org.sqlite.JDBC");

			String syncLiteDeviceDirStr = request.getSession().getAttribute("synclite-device-dir").toString();
			String jobName = request.getSession().getAttribute("job-name").toString();
			
			Path syncLiteDeviceDir;
			if ((syncLiteDeviceDirStr== null) || syncLiteDeviceDirStr.trim().isEmpty()) {
				throw new ServletException("\"SyncLite Device Directory Path\" must be specified");
			} else {
				syncLiteDeviceDir = Path.of(syncLiteDeviceDirStr);
				if (! Files.exists(syncLiteDeviceDir)) {
					Files.createDirectories(syncLiteDeviceDir);
				}

				if (! Files.exists(syncLiteDeviceDir)) {
					throw new ServletException("Specified \"SyncLite Device Directory Path\" : " + syncLiteDeviceDir + " does not exist, please specify a valid path.");
				}
			}

			initTracer(syncLiteDeviceDir);

			String syncliteLoggerConfigFileStr = request.getParameter("synclite-logger-configuration-file");
			Path loggerConfigPath;
			if ((syncliteLoggerConfigFileStr == null) || syncliteLoggerConfigFileStr.trim().isEmpty()) {
				throw new ServletException("\"SyncLite Logger Configuration File\" must be specified");
			} else {
				loggerConfigPath = Path.of(syncliteLoggerConfigFileStr);
				Path loggerConfigFileParent= Path.of(syncliteLoggerConfigFileStr).getParent();
				if (! Files.exists(loggerConfigFileParent)) {
					throw new ServletException("Specified \"SyncLite Logger Config File Path\" : " + syncliteLoggerConfigFileStr + " does not exist");
				}

			}

			String syncLiteLoggerConf = request.getParameter("synclite-logger-configuration");
			if ((syncLiteLoggerConf == null) || syncLiteLoggerConf.trim().isEmpty()) {
				throw new ServletException("\"SyncLite Logger Configurations\" must be specified");
			}

			String mqttBrokerURL = request.getParameter("mqtt-broker-url");
			if ((mqttBrokerURL == null) || mqttBrokerURL.trim().isEmpty()) {
				throw new ServletException("\"MQTT Broker URL\" must be specified");
			}

			String mqttBrokerUser = request.getParameter("mqtt-broker-user");
			String mqttBrokerPassword = request.getParameter("mqtt-broker-password");

			String mqttBrokerConnectionTimeoutS = request.getParameter("mqtt-broker-connection-timeout-s");
			try {
				if (Integer.valueOf(mqttBrokerConnectionTimeoutS) == null) {
					throw new ServletException("Please specify a valid numeric value for \"MQTT broker connection timeout in seconds\"");
				} else if (Long.valueOf(mqttBrokerConnectionTimeoutS) < 0) {
					throw new ServletException("Please specify a positive numeric value for \"MQTT broker connection timeout in seconds\"");
				}
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid numeric value for \"MQTT broker connection timeout in seconds\"");
			}

			testBrokerConnection(mqttBrokerURL, mqttBrokerUser, mqttBrokerPassword, mqttBrokerConnectionTimeoutS);

			String srcMessageFormat = request.getParameter("src-message-format");
			if ((srcMessageFormat == null) || srcMessageFormat.trim().isEmpty()) {
				throw new ServletException("\"Message Format\" must be specified");
			} else {
				switch(srcMessageFormat) {
				case "CSV":
					break;
				default:
					throw new ServletException("Specified \"Message Format\" :" + srcMessageFormat + " not supported");
				}
			}

			String mqttBrokerConnectionRetryIntervalS = request.getParameter("mqtt-broker-connection-retry-interval-s");
			try {
				if (Long.valueOf(mqttBrokerConnectionRetryIntervalS) == null) {
					throw new ServletException("Please specify a valid numeric value for \"MQTT broker connection retry interval in seconds\"");
				} else if (Long.valueOf(mqttBrokerConnectionRetryIntervalS) <= 0) {
					throw new ServletException("Please specify a positive numeric value for \"MQTT broker connection retry interval in seconds\"");
				}
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid numeric value for \"MQTT broker connection retry interval in seconds\"");
			}

			String mqttHeaderDelimiter = request.getParameter("mqtt-message-header-delimiter");
			if ((mqttHeaderDelimiter == null) || mqttHeaderDelimiter.trim().isEmpty()) {
				throw new ServletException("\"MQTT Message Header Delimiter\" must be specified");
			} else {
				if (mqttHeaderDelimiter.length() > 1) {
					throw new ServletException("\"MQTT Message Header Delimiter\" must be a single character delimiter");
				}
			}

			String srcMessageDelimiter = request.getParameter("src-message-field-delimiter");
			if ((srcMessageDelimiter == null) || srcMessageDelimiter.trim().isEmpty()) {
				throw new ServletException("\"Message Field Delimiter\" must be specified");
			} else {
				if (srcMessageDelimiter.length() > 1) {
					throw new ServletException("\"Message Field Delimiter\" must be a single character delimiter");
				}
			}

			String messageBatchProcessingStr = request.getParameter("qreader-message-batch-processing");
			try {
				if (Boolean.valueOf(messageBatchProcessingStr) == null) {
					throw new ServletException("Please specify a valid boolean value for \"Message Batch Processing\"");
				}				
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid boolean value for \"Message Batch Processing\"");
			}

			String messageBatchFlushIntervalMsStr = request.getParameter("qreader-message-batch-flush-interval-ms");
			try {
				if (Long.valueOf(messageBatchFlushIntervalMsStr) == null) {
					throw new ServletException("Please specify a valid numeric value for \"Message batch flush interval in milliseconds\"");
				} else if (Long.valueOf(messageBatchFlushIntervalMsStr) <= 0) {
					throw new ServletException("Please specify a positive numeric value for \"Message batch flush interval in milliseconds\"");
				}
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid numeric value for \"Message batch flush interval in milliseconds\"");
			}

			String syncLiteDeviceTypeStr = request.getParameter("qreader-synclite-device-type");
			String mapDevicesToSingleSyncLiteDeviceStr = request.getParameter("qreader-map-devices-to-single-synclite-device");
			try {
				if (Boolean.valueOf(mapDevicesToSingleSyncLiteDeviceStr) == null) {
					throw new ServletException("Please specify a valid boolean value for \"Map Devices To Single SyncLite Device\"");
				}				
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid boolean value for \"Map Devices To Single SyncLite Device\"");
			}	
		
			String defaultSyncLiteDeviceName = request.getParameter("qreader-default-synclite-device-name");
			if (! (defaultSyncLiteDeviceName.matches("^[a-zA-Z0-9]+$") && defaultSyncLiteDeviceName.length() <= 64)) {
				throw new ServletException("\"Default SyncLite Device Name\" must be an alphanumeric string with a length smaller than 64 characters");
			}

			String ignoreMessagesForUndefinedTopicsStr = request.getParameter("qreader-ignore-messages-for-undefined-topics");
			try {
				if (Boolean.valueOf(ignoreMessagesForUndefinedTopicsStr) == null) {
					throw new ServletException("Please specify a valid boolean value for \"Ignore Messages For Undefined Topics\"");
				}				
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid boolean value for \"Ignore Messages For Undefined Topics\"");
			}

			String defaultSyncLiteTableName = request.getParameter("qreader-default-synclite-table-name");
			if (! (defaultSyncLiteTableName.matches("[a-zA-Z_][a-zA-Z0-9_$]*") && defaultSyncLiteTableName.length() <= 64)) {
				throw new ServletException("\"Default SyncLite Table Name\" must be a valid table name as supported by SQLite and with length smaller than 64 characters");
			}

			String ignoreCorruptMessagesStr = request.getParameter("qreader-ignore-corrupt-messages");
			try {
				if (Boolean.valueOf(ignoreCorruptMessagesStr) == null) {
					throw new ServletException("Please specify a valid boolean value for \"Ignore Corrupt Messages\"");
				}				
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid boolean value for \"Ignore Corrupt Messages\"");
			}

			String corruptMessagesSyncLiteTableName = request.getParameter("qreader-corrupt-messages-synclite-table-name");
			if (! (corruptMessagesSyncLiteTableName.matches("[a-zA-Z_][a-zA-Z0-9_$]*") && corruptMessagesSyncLiteTableName.length() <= 64)) {
				throw new ServletException("\"SyncLite Table Name For Corrupt Messages\" must be as supported by SQLite and with length smaller than 64 characters");
			}

			String srcNumTopicsStr = request.getParameter("src-num-topics");
			try {
				if (Integer.valueOf(srcNumTopicsStr) == null) {
					throw new ServletException("Please specify a valid numeric value for \"Number of Topics\"");
				} else if (Integer.valueOf(srcNumTopicsStr) <= 0) {
					throw new ServletException("Please specify a positive numeric value for \"Number of Topics\"");
				}
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid numeric value for \"Number of Topics\"");
			}

			String jvmArguments = null;
			if (request.getParameter("jvm-arguments") != null) {
				if (!request.getParameter("jvm-arguments").isBlank()) {
					jvmArguments = request.getParameter("jvm-arguments");
				}
			}

			String mqttQOSLevelStr = request.getParameter("mqtt-qos-level");
			try {
				if (Integer.valueOf(mqttQOSLevelStr) == null) {
					throw new ServletException("Please specify a valid numeric value for \"MQTT Quality of Service Level\"");
				} else if ((Integer.valueOf(mqttQOSLevelStr) < 0) || (Integer.valueOf(mqttQOSLevelStr) > 2)) {
					throw new ServletException("Please specify a positive numeric value between 0 and 2 for \"MQTT Quality of Service Level\"");
				}
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid numeric value for \"MQTT Quality of Service Level\"");
			}

			String mqttCleanSessionStr = request.getParameter("mqtt-clean-session");
			try {
				if (Boolean.valueOf(mqttCleanSessionStr) == null) {
					throw new ServletException("Please specify a valid boolean value for \"MQTT Clean Session\"");
				}				
			} catch (NumberFormatException e) {
				throw new ServletException("Please specify a valid boolean value for \"MQTT Clean Session\"");
			}

			String qReaderTraceLevel = request.getParameter("qreader-trace-level");

			request.getSession().setAttribute("synclite-device-dir", syncLiteDeviceDirStr);
			request.getSession().setAttribute("synclite-logger-configuration-file", syncliteLoggerConfigFileStr);

			request.getSession().setAttribute("mqtt-broker-url", mqttBrokerURL);
			if ((mqttBrokerUser != null) && (!mqttBrokerUser.isBlank())) {
				request.getSession().setAttribute("mqtt-broker-user", mqttBrokerUser);
			} else {
				request.getSession().removeAttribute("mqtt-broker-user");
			}
			if ((mqttBrokerPassword != null) && (!mqttBrokerPassword.isBlank())) {
				request.getSession().setAttribute("mqtt-broker-password", mqttBrokerPassword);
			} else {
				request.getSession().removeAttribute("mqtt-broker-password");
			}
			request.getSession().setAttribute("mqtt-broker-connection-timeout-s", mqttBrokerConnectionTimeoutS);
			request.getSession().setAttribute("mqtt-broker-connection-retry-interval-s", mqttBrokerConnectionRetryIntervalS);
			request.getSession().setAttribute("mqtt-message-header-delimiter", mqttHeaderDelimiter);
			request.getSession().setAttribute("mqtt-qos-level", mqttQOSLevelStr);
			request.getSession().setAttribute("mqtt-clean-session", mqttCleanSessionStr);

			request.getSession().setAttribute("src-message-format", srcMessageFormat);
			request.getSession().setAttribute("src-message-field-delimiter", srcMessageDelimiter);
			request.getSession().setAttribute("src-num-topics", srcNumTopicsStr);

			request.getSession().setAttribute("qreader-trace-level", qReaderTraceLevel);
			request.getSession().setAttribute("qreader-synclite-device-type", syncLiteDeviceTypeStr);
			request.getSession().setAttribute("qreader-map-devices-to-single-synclite-device", mapDevicesToSingleSyncLiteDeviceStr);
			request.getSession().setAttribute("qreader-default-synclite-device-name", defaultSyncLiteDeviceName);
			request.getSession().setAttribute("qreader-ignore-messages-for-undefined-topics", ignoreMessagesForUndefinedTopicsStr);
			request.getSession().setAttribute("qreader-default-synclite-table-name", defaultSyncLiteTableName);
			request.getSession().setAttribute("qreader-ignore-corrupt-messages", ignoreCorruptMessagesStr);
			request.getSession().setAttribute("qreader-corrupt-messages-synclite-table-name", corruptMessagesSyncLiteTableName);
			request.getSession().setAttribute("qreader-message-batch-processing", messageBatchProcessingStr);
			request.getSession().setAttribute("qreader-message-batch-flush-interval-ms", messageBatchFlushIntervalMsStr);
			if (jvmArguments != null) {
				request.getSession().setAttribute("jvm-arguments", jvmArguments);
			} else {
				request.getSession().removeAttribute("jvm-arguments");
			}

			//Create mqttreader config string.
			StringBuilder builder = new StringBuilder();
			builder.append("job-name = ").append(jobName).append("\n");
			builder.append("synclite-device-dir = ").append(syncLiteDeviceDirStr).append("\n");
			builder.append("synclite-logger-configuration-file = ").append(syncliteLoggerConfigFileStr).append("\n");
			builder.append("mqtt-broker-url = ").append(mqttBrokerURL).append("\n");
			if ((mqttBrokerUser != null) && (!mqttBrokerUser.isBlank())) {
				builder.append("mqtt-broker-user = ").append(mqttBrokerUser).append("\n");
			}
			if ((mqttBrokerPassword != null) && (!mqttBrokerPassword.isBlank())) {
				builder.append("mqtt-broker-password = ").append(mqttBrokerPassword).append("\n");
			}
			builder.append("mqtt-broker-connection-timeout-s = ").append(mqttBrokerConnectionTimeoutS).append("\n");
			builder.append("mqtt-broker-connection-retry-interval-s = ").append(mqttBrokerConnectionRetryIntervalS).append("\n");
			builder.append("mqtt-message-header-delimiter = ").append(mqttHeaderDelimiter).append("\n");
			builder.append("mqtt-qos-level = ").append(mqttQOSLevelStr).append("\n");
			builder.append("mqtt-clean-session = ").append(mqttCleanSessionStr).append("\n");

			builder.append("src-message-format = ").append(srcMessageFormat).append("\n");
			builder.append("src-message-field-delimiter = ").append(srcMessageDelimiter).append("\n");

			builder.append("qreader-trace-level = ").append(qReaderTraceLevel).append("\n");
			builder.append("qreader-synclite-device-type = ").append(syncLiteDeviceTypeStr).append("\n");
			builder.append("qreader-map-devices-to-single-synclite-device = ").append(mapDevicesToSingleSyncLiteDeviceStr).append("\n");
			builder.append("qreader-default-synclite-device-name = ").append(defaultSyncLiteDeviceName).append("\n");
			builder.append("qreader-ignore-messages-for-undefined-topics = ").append(ignoreMessagesForUndefinedTopicsStr).append("\n");
			builder.append("qreader-default-synclite-table-name = ").append(defaultSyncLiteTableName).append("\n");			
			builder.append("qreader-ignore-corrupt-messages = ").append(ignoreCorruptMessagesStr).append("\n");
			builder.append("qreader-corrupt-messages-synclite-table-name = ").append(corruptMessagesSyncLiteTableName).append("\n");			
			builder.append("qreader-message-batch-processing = ").append(messageBatchProcessingStr).append("\n");
			builder.append("qreader-message-batch-flush-interval-ms = ").append(messageBatchFlushIntervalMsStr).append("\n");
			String qReaderConfPath = Path.of(syncLiteDeviceDirStr, "synclite_qreader.conf").toString();

			try {
				Files.writeString(Path.of(qReaderConfPath), builder.toString());
			} catch (IOException e) {
				this.globalTracer.error("Failed to write SyncLite qreader configurations into file : " + qReaderConfPath, e);	
				throw new ServletException("Failed to write SyncLite qreader configurations into file : " + qReaderConfPath, e);
			}

			//Now write out SyncLite logger configurations

			try {
				Files.writeString(Path.of(syncliteLoggerConfigFileStr), syncLiteLoggerConf);
			} catch (IOException e) {
				this.globalTracer.error("Failed to write SyncLite logger configurations into file : " + syncliteLoggerConfigFileStr, e);
				throw new ServletException("Failed to write SyncLite logger configurations into file : " + syncliteLoggerConfigFileStr, e);
			}

			//Next step is to fetch all table metadata

			Path qReaderMetadataFilePath = Path.of(syncLiteDeviceDirStr, "synclite_qreader_metadata.db");
			createMetadataTable(qReaderMetadataFilePath);

			//request.getRequestDispatcher("configureMQTTTables.jsp").forward(request, response);
			response.sendRedirect("configureMQTTTables.jsp");

		} catch (Exception e) {
			//System.out.println("exception : " + e);
			this.globalTracer.error("Exception while processing request:", e);
			String errorMsg = e.getMessage();
			request.getRequestDispatcher("configureMQTTReader.jsp?errorMsg=" + errorMsg).forward(request, response);
			throw new ServletException(e);
		}
	}
	private final void testBrokerConnection(String mqttBrokerURL, String user, String password, String timeout) throws ServletException {
		try(MqttClient client = new MqttClient(mqttBrokerURL, MqttClient.generateClientId())) {
			MqttConnectOptions connOpts = new MqttConnectOptions();
			if ((user != null) && (!user.isBlank())) {
				connOpts.setUserName(user);
			}
			if ((password != null) && (!password.isBlank())) {
				connOpts.setPassword(password.toCharArray());
			}
			connOpts.setConnectionTimeout(Integer.valueOf(timeout)); // Set connection timeout in seconds
			client.connect(connOpts);
			client.disconnect();
		} catch (Exception e) {
			// Handle the exception if needed
			throw new ServletException("Unable to connect to the specified MQTT broker url : ", e);
		}
	}	

	private void createMetadataTable(Path metadataFilePath) throws ServletException {
		String url = "jdbc:sqlite:" + metadataFilePath;
		String createMetadataTableSql = "CREATE TABLE IF NOT EXISTS topic_info(topic_name TEXT PRIMARY KEY, topic_table_name TEXT, topic_field_count LONG, topic_create_table_sql TEXT, topic_table_column_list TEXT, enable INTEGER)";
		try (Connection conn = DriverManager.getConnection(url)){
			try (Statement stmt = conn.createStatement()) {
				stmt.execute(createMetadataTableSql);
			}
		} catch(SQLException e) {
			this.globalTracer.error("Failed to create qreader metadata table : ", e);
			throw new ServletException("Failed to create qreader metadata table : ", e);
		}
	}


	private final void initTracer(Path workDir) {
		this.globalTracer = Logger.getLogger(ValidateMQTTReader.class);
		if (this.globalTracer.getAppender("QReaderTracer") == null) {
			globalTracer.setLevel(Level.INFO);
			RollingFileAppender fa = new RollingFileAppender();
			fa.setName("QReaderTracer");
			fa.setFile(workDir.resolve("synclite_qreader.trace").toString());
			fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
			fa.setMaxBackupIndex(10);
			fa.setAppend(true);
			fa.activateOptions();
			globalTracer.addAppender(fa);
		}
	}
}

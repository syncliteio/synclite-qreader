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

package com.synclite.qreader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConfLoader {

	private static final class InstanceHolder {
		private static ConfLoader INSTANCE = new ConfLoader();
	}

	public static ConfLoader getInstance() {
		return InstanceHolder.INSTANCE;
	}

	private HashMap<String, String> properties;
	private Path syncLiteDeviceDir;
	private Path syncLiteLoggerConfigurationFile;
	private String mqttBrokerURL;
	private String mqttBrokerUser;
	private String mqttBrokerPassword;
	private TraceLevel traceLevel;
	private Integer mqttBrokerConnectionTimeoutS;
	private Long mqttBrokerConnectionRetryIntervalS;
	private MessageFormat messageFormat;
	private String mqttMessageHeaderDelimiter;
	private String messageFieldDelimiter;
	private Integer mqttQOSLevel;
	private Boolean mqttCleanSession;
	private Boolean messageBatchProcessing;
	private Long messageBatchFlushIntervalMs;
	private SyncLiteDeviceType syncLiteDeviceType;
	private Boolean mapDevicesToSingleSyncLiteDevice;
	private String defaultSyncLiteDeviceName;
	private Boolean ignoreMessagesForUndefinedTopics;
	private String defaultSyncLiteTableName;
	private Boolean ignoreCorruptMessages;
	private String corruptMessagesSyncLiteTableName;
	
	public final Path getSyncLiteDeviceDir() {
		return syncLiteDeviceDir;
	}

	public final Path getSyncLiteLoggerConfigurationFile() {
		return syncLiteLoggerConfigurationFile;
	}

	public final Integer getMQTTQoSLevel() {
		return mqttQOSLevel;
	}

	public final Boolean getMQTTCleanSession() {
		return mqttCleanSession;
	}

	public final String getMQTTBrokerURL() {
		return mqttBrokerURL;
	}

	public final String getMQTTBrokerUser() {
		return mqttBrokerUser;
	}

	public final String getMQTTBrokerPassword() {
		return mqttBrokerPassword;
	}

	private ConfLoader() {

	}

	public void loadQReaderConfigProperties(Path propsPath) throws SyncLiteException {
		this.properties = loadPropertiesFromFile(propsPath);
		validateAndProcessProperties();    	
	}

	public static HashMap<String, String> loadPropertiesFromFile(Path propsPath) throws SyncLitePropsException {
		BufferedReader reader = null;
		try {
			HashMap<String, String> properties = new HashMap<String, String>();
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
				String[] tokens = line.split("=", 2);
				if (tokens.length < 2) {
					if (tokens.length == 1) {
						if (tokens[0].startsWith("=")) {
							throw new SyncLitePropsException("Invalid line in configuration file " + propsPath + " : " + line);
						}
					} else { 
						throw new SyncLitePropsException("Invalid line in configuration file " + propsPath + " : " + line);
					}
				}
				properties.put(tokens[0].trim().toLowerCase(), line.substring(line.indexOf("=") + 1, line.length()).trim());
				line = reader.readLine();
			}
			return properties;
		} catch (IOException e) {
			throw new SyncLitePropsException("Failed to load configuration file : " + propsPath + " : ", e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					throw new SyncLitePropsException("Failed to close configuration file : " + propsPath + ": " , e);
				}
			}
		}
	}

	private void validateAndProcessProperties() throws SyncLitePropsException {
		String propValue = properties.get("synclite-device-dir");
		if (propValue != null) {
			this.syncLiteDeviceDir = Path.of(propValue);
			if (this.syncLiteDeviceDir == null) {
				throw new SyncLitePropsException("Invalid value specified for synclite-device-dir in configuration file");
			}
			if (!Files.exists(this.syncLiteDeviceDir)) {
				throw new SyncLitePropsException("Specified synclite-device-dir path does not exist : " + this.syncLiteDeviceDir);
			}
			if (!this.syncLiteDeviceDir.toFile().canRead()) {
				throw new SyncLitePropsException("No read permission on specified synclite-device-dir path");
			}
			if (!this.syncLiteDeviceDir.toFile().canWrite()) {
				throw new SyncLitePropsException("No write permission on specified synclite-device-dir path");
			}
		} else {
			throw new SyncLitePropsException("synclite-device-dir not specified in configuration file");
		}

		propValue = properties.get("synclite-logger-configuration-file");
		if (propValue != null) {
			this.syncLiteLoggerConfigurationFile= Path.of(propValue);
			if (this.syncLiteLoggerConfigurationFile == null) {
				throw new SyncLitePropsException("Invalid value specified for synclite-logger-configuration-file in configuration file");
			}
			if (!Files.exists(this.syncLiteLoggerConfigurationFile)) {
				throw new SyncLitePropsException("Specified synclite-logger-configuration-file does not exist : " + syncLiteLoggerConfigurationFile);
			}
			if (!this.syncLiteLoggerConfigurationFile.toFile().canRead()) {
				throw new SyncLitePropsException("No read permission on specified synclite-logger-configuration-file path");
			}
		} else {
			throw new SyncLitePropsException("synclite-logger-configuration-file not specified in configuration file");
		}

		propValue = properties.get("mqtt-qos-level");
		if (propValue != null) {
			try {
				this.mqttQOSLevel = Integer.valueOf(propValue);
				if (this.mqttQOSLevel == null) {
					throw new SyncLitePropsException("Invalid value specified for mqtt-qos-level in configuration file");
				} else if ((this.mqttQOSLevel < 0) || (this.mqttQOSLevel > 2)) {
					throw new SyncLitePropsException("Please specify a value between 0 to 2 for mqtt-qos-level in configuration file");
				}
				
			} catch (NumberFormatException e) {
				throw new SyncLitePropsException("Please specify a value between 0 to 2 for mqtt-qos-level in configuration file");
			}
		} else {
			this.mqttQOSLevel = 0;
		}

		propValue = properties.get("mqtt-clean-session");
		if (propValue != null) {
			try {
				this.mqttCleanSession = Boolean.valueOf(propValue);
				if (this.mqttCleanSession == null) {
					throw new SyncLitePropsException("Invalid value specified for mqtt-clean-session in configuration file");
				}				
			} catch (NumberFormatException e) {
				throw new SyncLitePropsException("Invalid value specified for mqtt-clean-session in configuration file");
			}
		} else {
			this.mqttCleanSession = false;
		}

		propValue = properties.get("mqtt-broker-connection-timeout-s");
		if (propValue != null) {
			try {
				this.mqttBrokerConnectionTimeoutS = Integer.valueOf(propValue);
				if (this.mqttBrokerConnectionTimeoutS == null) {
					throw new SyncLitePropsException("Invalid value specified for mqtt-broker-connection-timeout-s in configuration file");
				} else if (this.mqttBrokerConnectionTimeoutS < 0) {
					throw new SyncLitePropsException("Please specify a positive numeric value for mqtt-broker-connection-timeout-s in configuration file");
				}
			} catch (NumberFormatException e) {
				throw new SyncLitePropsException("Please specify a positive numeric value for mqtt-broker-connection-timeout-s in configuration file");
			}
		} else {
			this.mqttBrokerConnectionTimeoutS = 30;
		}

		propValue = properties.get("mqtt-broker-connection-retry-interval-s");
		if (propValue != null) {
			try {
				this.mqttBrokerConnectionRetryIntervalS = Long.valueOf(propValue);
				if (this.mqttBrokerConnectionRetryIntervalS == null) {
					throw new SyncLitePropsException("Invalid value specified for mqtt-broker-connection-retry-interval-s in configuration file");
				} else if (this.mqttBrokerConnectionRetryIntervalS <= 0) {
					throw new SyncLitePropsException("Please specify a positive numeric value for mqtt-broker-connection-retry-interval-s in configuration file");
				}
			} catch (NumberFormatException e) {
				throw new SyncLitePropsException("Please specify a positive numeric value for mqtt-broker-connection-retry-interval-s in configuration file");
			}
		} else {
			this.mqttBrokerConnectionRetryIntervalS = 5L;
		}

		propValue = properties.get("qreader-synclite-device-type");
		if (propValue != null) {
			try {
				this.syncLiteDeviceType = SyncLiteDeviceType.valueOf(propValue);
				if (this.syncLiteDeviceType == null) {
					throw new SyncLitePropsException("Invalid value specified for qreader-synclite-device-type in configuration file");
				}				
			} catch (IllegalArgumentException e) {
				throw new SyncLitePropsException("Invalid value specified for qreader-synclite-device-type in configuration file");
			}
		} else {
			this.syncLiteDeviceType = SyncLiteDeviceType.TELEMETRY;
		}
		
		propValue = properties.get("qreader-map-devices-to-single-synclite-device");
		if (propValue != null) {
			try {
				this.mapDevicesToSingleSyncLiteDevice = Boolean.valueOf(propValue);
				if (this.mapDevicesToSingleSyncLiteDevice == null) {
					throw new SyncLitePropsException("Invalid value specified for qreader-synclite-device-type in configuration file");
				}				
			} catch (IllegalArgumentException e) {
				throw new SyncLitePropsException("Invalid value specified for qreader-synclite-device-type in configuration file");
			}
		} else {
			this.mapDevicesToSingleSyncLiteDevice = true;
		}
		
		propValue = properties.get("qreader-default-synclite-device-name");
		if (propValue != null) {
			this.defaultSyncLiteDeviceName = propValue;
			if (! (this.defaultSyncLiteDeviceName.matches("^[a-zA-Z0-9]+$") && this.defaultSyncLiteDeviceName.length() <= 64)) {
				throw new SyncLitePropsException("qreader-default-synclite-device-name must be an alphanumeric string with length smaller than 64 characters");
			}
		} else {
			this.defaultSyncLiteDeviceName = "default";
		}

		propValue = properties.get("qreader-ignore-messages-for-undefined-topics");
		if (propValue != null) {
			try {
				this.ignoreMessagesForUndefinedTopics = Boolean.valueOf(propValue);
				if (this.ignoreMessagesForUndefinedTopics == null) {
					throw new SyncLitePropsException("Invalid value specified for qreader-ignore-messages-for-undefined-topics in configuration file");
				}				
			} catch (IllegalArgumentException e) {
				throw new SyncLitePropsException("Invalid value specified for qreader-ignore-messages-for-undefined-topics in configuration file");
			}
		} else {
			this.ignoreMessagesForUndefinedTopics = false;
		}

		propValue = properties.get("qreader-default-synclite-table-name");
		if (propValue != null) {
			this.defaultSyncLiteTableName = propValue;
			if (! (this.defaultSyncLiteTableName.matches("[a-zA-Z_][a-zA-Z0-9_$]*") && this.defaultSyncLiteTableName.length() <= 64)) {
				throw new SyncLitePropsException("\"qreader-default-synclite-table-name\" must be a valid table name as supported by SQLite and with length smaller than 64 characters");
			}

		} else {
			this.defaultSyncLiteTableName = "default_table";
		}

		propValue = properties.get("qreader-ignore-corrupt_messages");
		if (propValue != null) {
			try {
				this.ignoreCorruptMessages = Boolean.valueOf(propValue);
				if (this.ignoreCorruptMessages == null) {
					throw new SyncLitePropsException("Invalid value specified for qreader-ignore-corrupt_messages in configuration file");
				}				
			} catch (IllegalArgumentException e) {
				throw new SyncLitePropsException("Invalid value specified for qreader-ignore-corrupt_messages in configuration file");
			}
		} else {
			this.ignoreCorruptMessages = false;
		}
		
		propValue = properties.get("qreader-corrupt-messages-synclite-table-name");
		if (propValue != null) {
			this.corruptMessagesSyncLiteTableName = propValue;
			if (! (this.corruptMessagesSyncLiteTableName.matches("[a-zA-Z_][a-zA-Z0-9_$]*") && this.corruptMessagesSyncLiteTableName.length() <= 64)) {
				throw new SyncLitePropsException("qreader-corrupt-messages-synclite-table-name must be a valid table name as supported by SQLite and with length smaller than 64 characters");
			}
		} else {
			this.corruptMessagesSyncLiteTableName = "corrupt_messages";
		}

		propValue = properties.get("qreader-message-batch-processing");
		if (propValue != null) {
			try {
				this.messageBatchProcessing = Boolean.valueOf(propValue);
				if (this.messageBatchProcessing == null) {
					throw new SyncLitePropsException("Invalid value specified for qreader-message-batch-processing in configuration file");
				}				
			} catch (NumberFormatException e) {
				throw new SyncLitePropsException("Invalid value specified for qreader-message-batch-processing in configuration file");
			}
		} else {
			this.messageBatchProcessing = true;
		}

		propValue = properties.get("qreader-message-batch-flush-interval-ms");
		if (propValue != null) {
			try {
				this.messageBatchFlushIntervalMs  = Long.valueOf(propValue);
				if (this.messageBatchFlushIntervalMs  == null) {
					throw new SyncLitePropsException("Invalid value specified for qreader-message-batch-flush-interval-ms in configuration file");
				} else if (this.messageBatchFlushIntervalMs  <= 0) {
					throw new SyncLitePropsException("Please specify a positive numeric value for qreader-message-batch-flush-interval-ms in configuration file");
				}
			} catch (NumberFormatException e) {
				throw new SyncLitePropsException("Please specify a positive numeric value for qreader-message-batch-flush-interval-ms in configuration file");
			}
		} else {
			this.messageBatchFlushIntervalMs = 5000L;
		}
		
		propValue = properties.get("mqtt-broker-url");
		if (propValue != null) {
			this.mqttBrokerURL = propValue;
		} else {
			throw new SyncLitePropsException("Please specify a valid mqtt-broker-url");
		}

		propValue = properties.get("mqtt-broker-user");
		if (propValue != null) {
			this.mqttBrokerUser = propValue;
		} 

		propValue = properties.get("mqtt-broker-password");
		if (propValue != null) {
			this.mqttBrokerPassword = propValue;
		} 
		
		propValue = properties.get("qreader-trace-level");
		if (propValue != null) {
			this.traceLevel= TraceLevel.valueOf(propValue);
			if (this.traceLevel == null) {
				throw new SyncLitePropsException("Invalid value specified for qreader-trace-level in configuration file");
			}
		} else {
			traceLevel = TraceLevel.DEBUG;
		}

		propValue = properties.get("mqtt-message-header-delimiter");
		if (propValue != null) {
			if (propValue.length() != 1) {
				throw new SyncLitePropsException("Invalid value specified for mqtt-message-header-delimiter in configuration file");
			}
			this.mqttMessageHeaderDelimiter = propValue;
		} else {
			throw new SyncLitePropsException("Please specify mqtt-message-header-delimiter that separates device name and topic name e.g. <deviceName>/<topicName>");		
		}

		propValue = properties.get("src-message-field-delimiter");
		if (propValue != null) {
			if (propValue.length() != 1) {
				throw new SyncLitePropsException("Invalid value specified for src-message-field-delimiter in configuration file");
			}
			this.messageFieldDelimiter = propValue;
		} else {
			throw new SyncLitePropsException("Please specify src-message-field-delimiter that separates indivoidual field values in message payload");		
		}

		propValue = properties.get("src-message-format");
		if (propValue != null) {
			this.messageFormat = MessageFormat.valueOf(propValue);
			if (this.messageFormat == null) {
				throw new SyncLitePropsException("Invalid value specified for src-message-format in configuration file");
			}
		} else {
			messageFormat= MessageFormat.CSV;
		}

	}

	public TraceLevel getTraceLevel() {
		return this.traceLevel;
	}

	public int getBrokerConnectionTimeoutS() {
		return mqttBrokerConnectionTimeoutS;
	}

	public long getBrokerConnectionRetryIntervalS() {
		return mqttBrokerConnectionRetryIntervalS;
	}

	public MessageFormat getMessageFormat() {
		return messageFormat;
	}

	public String getMQTTMessageHeaderDelimiter() {
		return this.mqttMessageHeaderDelimiter;
	}

	public String getMessageFieldDelimiter() {
		return this.messageFieldDelimiter;
	}

	public final Boolean getMessageBatchProcessing() {
		return messageBatchProcessing;
	}

	public long getMessageBatchFlushIntervalMs() {
		return this.messageBatchFlushIntervalMs;
	}

	public SyncLiteDeviceType getSyncLiteDeviceType() {
		return this.syncLiteDeviceType;
	}	
	
	public boolean getMapDevicesToSingleSyncLiteDevice() {
		return this.mapDevicesToSingleSyncLiteDevice;
	}
	
	public String getDefaultSyncLiteDeviceName() {
		return this.defaultSyncLiteDeviceName;
	}

	public boolean getIgnoreMessagesForUndefinedTopics() {
		return this.ignoreMessagesForUndefinedTopics;		
	}
	
	public String getDefaultSyncLiteTableName() {
		return this.defaultSyncLiteTableName;
	}

	public boolean getIgnoreCorruptMessages() {
		return this.ignoreCorruptMessages;		
	}

	public String getCorruptMessagesSyncLiteTableName() {
		return this.corruptMessagesSyncLiteTableName;
	}

}

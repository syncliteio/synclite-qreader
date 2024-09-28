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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


/*import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
 */


public class QReaderDriver implements Runnable{
	
	protected abstract class DeviceWriterCreator {
		protected abstract DeviceWriter getOrCreateDeviceWriter(String deviceName, Logger tracer);
	}

	private class FixedDeviceWriterCreator extends DeviceWriterCreator{
		private final DeviceWriter fixedDeviceWriter;
		
		private FixedDeviceWriterCreator() {			
			this.fixedDeviceWriter = DeviceWriter.getInstance(ConfLoader.getInstance().getDefaultSyncLiteDeviceName(), globalTracer);
		}
		
		protected DeviceWriter getOrCreateDeviceWriter(String deviceName, Logger tracer) {
			return this.fixedDeviceWriter;
		}
	}

	private class DynamicDeviceWriterCreator extends DeviceWriterCreator {
		protected DeviceWriter getOrCreateDeviceWriter(String deviceName, Logger tracer) {
			return DeviceWriter.getInstance(deviceName, tracer);
		}
	}

	
	private abstract class MessageProcessor {
		protected abstract void processMessage(String header, byte[] message) throws SyncLiteException;

		protected final Message parseMessage(String header, byte[] message) {
			String[] tokens = header.split(ConfLoader.getInstance().getMQTTMessageHeaderDelimiter());
			String deviceName = "UNKNOWN";
			String topicName = "UNKNOWN";
			if (tokens.length >= 2) {
				deviceName = tokens[0];
				topicName = tokens[1];
			} else if (tokens.length == 1){
				deviceName = ConfLoader.getInstance().getDefaultSyncLiteDeviceName();
				topicName = tokens[0];
			}
			Message m = new Message(deviceName, topicName, message);
			return m;
		}
	}

	private class SyncMessageProcessor extends MessageProcessor{
		protected void processMessage(String header, byte[] message) throws SyncLiteException {
			try {
				//System.out.println("Received message: " + new String(message.getPayload()));
				Message m = parseMessage(header, message);
				DeviceWriter deviceWriter = deviceWriterCreator.getOrCreateDeviceWriter(m.deviceName, globalTracer);
				deviceWriter.writeAndCommitMessage(m);
			} catch(Exception e) {
				String payload = new String(message);
				globalTracer.error("Failed to process message with header : " + header + " content : " + payload  + " with exception : ", e);                    		
				throw new SyncLiteException("Failed to process message with header : " + header + " content : " + payload  + " with exception : ", e);
			}
		}
	}

	private class AsyncMessageProcessor extends MessageProcessor{
		private HashSet<DeviceWriter> dirtyDeviceWriters = new HashSet<DeviceWriter>();
		private BlockingQueue<RawMessage> msgQ = new LinkedBlockingQueue<RawMessage>(Integer.MAX_VALUE);
		private ScheduledExecutorService msgProcessor;
		private AsyncMessageProcessor() {
			msgProcessor = Executors.newScheduledThreadPool(1);
			msgProcessor.scheduleAtFixedRate(this::writeMessages, 0, ConfLoader.getInstance().getMessageBatchFlushIntervalMs(), TimeUnit.MILLISECONDS);
		}
		protected void processMessage(String header, byte[] message) throws SyncLiteException {
			try {
				RawMessage m = new RawMessage(header, message);
				//Add to queue and move on
				msgQ.add(m);
			} catch (Exception e) {
				String payload = new String(message);
				throw new SyncLiteException("Failed to process message with header : " + header + " content : " + payload  + " with exception : ", e);
			}
		}

		protected void writeMessages() {
			try {
				dirtyDeviceWriters.clear();
				while(!msgQ.isEmpty()) {
					RawMessage rm = msgQ.poll();
					if (rm == null) {
						return;
					}
					Message m = parseMessage(rm.header, rm.payload);
					DeviceWriter deviceWriter = deviceWriterCreator.getOrCreateDeviceWriter(m.deviceName, globalTracer);
					deviceWriter.writeMessage(m);
					dirtyDeviceWriters.add(deviceWriter);
				}

				for (DeviceWriter d : dirtyDeviceWriters) {
					d.commitMessages();
				}
				dirtyDeviceWriters.clear();
			} catch (Exception e) {
				globalTracer.error("Failed to process batch of messages with exception : ", e);
			}
		}
	}
	
	private static final class InstanceHolder {
		private static QReaderDriver INSTANCE = new QReaderDriver();
	}

	public static QReaderDriver getInstance() {
		return InstanceHolder.INSTANCE;
	}

	private Logger globalTracer;
	private Path qReaderMetadataFile;
	private MessageProcessor msgProcessor;
	private DeviceWriterCreator deviceWriterCreator;

	//ScheduledExecutorService externalCommandLoader;

	private QReaderDriver() {
	}
	private final void initLogger() {
		this.globalTracer = Logger.getLogger(QReaderDriver.class);    	
		switch (ConfLoader.getInstance().getTraceLevel()) {
		case ERROR:
			globalTracer.setLevel(Level.ERROR);
			break;
		case INFO:
			globalTracer.setLevel(Level.INFO);
			break;
		case DEBUG:
			globalTracer.setLevel(Level.DEBUG);
			break;
		}
		RollingFileAppender fa = new RollingFileAppender();
		fa.setName("SyncLiteQReaderTracer");
		fa.setFile(Path.of(ConfLoader.getInstance().getSyncLiteDeviceDir().toString(), "synclite_qreader.trace").toString());
		fa.setLayout(new PatternLayout("%d %-5p [%t] %m%n"));
		fa.setMaxBackupIndex(10);
		fa.setMaxFileSize("10MB"); // Set the maximum file size to 10 MB
		fa.setAppend(true);
		fa.activateOptions();
		globalTracer.addAppender(fa);
		globalTracer.info("Starting READ job");
	}

	public final Logger getTracer() {
		return this.globalTracer;
	}

	private final void initDriver() throws SyncLiteException {
		initLogger();
		initSyncLite();
		initProcessors();
		loadTopics();
	}
	
	private final void runReadServices() throws SyncLiteException {
		initClient();
	}

	private final void initProcessors() {
		if (ConfLoader.getInstance().getMapDevicesToSingleSyncLiteDevice()) {
			this.deviceWriterCreator = new FixedDeviceWriterCreator();
		} else {
			this.deviceWriterCreator = new DynamicDeviceWriterCreator();
		}

		if (ConfLoader.getInstance().getMessageBatchProcessing()) {
			this.msgProcessor = new AsyncMessageProcessor();
		} else {
			this.msgProcessor = new SyncMessageProcessor();
		}
	}

	private final void initSyncLite() throws SyncLiteException {
		try {
			Class.forName("io.synclite.logger.Telemetry");
			Class.forName("io.synclite.logger.SQLiteAppender");
			Class.forName("io.synclite.logger.DuckDBAppender");
			Class.forName("io.synclite.logger.DerbyAppender");
			Class.forName("io.synclite.logger.H2Appender");
			Class.forName("io.synclite.logger.HyperSQLAppender");
		} catch (ClassNotFoundException e) {
			globalTracer.error("Failed to load SyncLite logger : ", e);
			throw new SyncLiteException("Failed to load SyncLite logger : ", e);
		}
	}

	private final void initClient() {
		MqttClient[] client = {null}; // Create an array to hold the final reference

		while (true) {
			try {
				client[0] = new MqttClient(ConfLoader.getInstance().getMQTTBrokerURL(), "synclite-qreader");

				MqttConnectOptions connOpts = new MqttConnectOptions();
				connOpts.setCleanSession(ConfLoader.getInstance().getMQTTCleanSession());
				connOpts.setConnectionTimeout(ConfLoader.getInstance().getBrokerConnectionTimeoutS());

				client[0].setCallback(new MqttCallback() {
					@Override
					public void connectionLost(Throwable cause) {
						globalTracer.error("Connection to broker lost : " + cause.getMessage());
						try {
							Thread.sleep(ConfLoader.getInstance().getBrokerConnectionRetryIntervalS() * 1000); // Wait before attempting to reconnect
							client[0].connect(connOpts); 
							client[0].subscribe("#", ConfLoader.getInstance().getMQTTQoSLevel());
							globalTracer.info("Successfully connected with broker");
						} catch (InterruptedException e) {
							Thread.interrupted();
						} catch (Exception e) {
							globalTracer.error("Failed to connect with broker, connection will be retried : ", e);
						}
					}

					@Override
					public void messageArrived(String header, MqttMessage message) throws Exception {
						try {
							//globalTracer.info("Message arrived : " + message.toString());
							msgProcessor.processMessage(header, message.getPayload());
						} catch(Exception e) {
							globalTracer.error("Message processing failed with exception : ", e);                    		
							//throw e;
						}
					}

					@Override
					public void deliveryComplete(IMqttDeliveryToken token) {
						// Not used in this example
					}
				});

				client[0].connect(connOpts);
				client[0].subscribe("#", ConfLoader.getInstance().getMQTTQoSLevel()); 

				globalTracer.info("Successfully connected with broker");

				// Keep the program running until manually interrupted (e.g., by pressing Ctrl+C)
				while (true) {
					try {
						Thread.sleep(1000);
						if (client[0] != null && client[0].isConnected()) {
							continue;
						} else {
							break;
						}
					} catch (InterruptedException e) {
						Thread.interrupted();
					}
				}

			} catch (Exception e) {
				globalTracer.error("Failed to connect with broker, connection will be retried : ", e);
			} finally {
				// Close the client before attempting to create a new one
				if (client[0] != null && client[0].isConnected()) {
					try {
						client[0].disconnect();
					} catch (MqttException e) {
						globalTracer.error("Error disconnecting from broker: ", e);
					}
				}
			}

			//Wait and reconnect
			try {
				Thread.sleep(ConfLoader.getInstance().getBrokerConnectionRetryIntervalS() * 1000); // Wait before attempting to reconnect            
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		}
	}
	private void loadTopics() throws SyncLiteException {
		//Load devices from metadata file
		try {
			this.qReaderMetadataFile = ConfLoader.getInstance().getSyncLiteDeviceDir().resolve("synclite_qreader_metadata.db");
			String url = "jdbc:sqlite:" + qReaderMetadataFile;
			Class.forName("org.sqlite.JDBC");
			try (Connection conn = DriverManager.getConnection(url)) {
				try(Statement stmt = conn.createStatement()) {
					try (ResultSet rs = stmt.executeQuery("SELECT topic_name, topic_table_name, topic_field_count, topic_create_table_sql, topic_table_column_list from topic_info where enable = 1")) {
						while (rs.next()) {						
							Topic.createInstance(rs.getString("topic_name"), rs.getString("topic_table_name"), rs.getLong("topic_field_count"), rs.getString("topic_create_table_sql"), rs.getString("topic_table_column_list"));
						}
					}
				}
			}
			//This topic to hold all unparsable messages for defined topics.
			Topic.createInstance(ConfLoader.getInstance().getCorruptMessagesSyncLiteTableName(), ConfLoader.getInstance().getCorruptMessagesSyncLiteTableName(), 2, "create table " + ConfLoader.getInstance().getCorruptMessagesSyncLiteTableName() + "(topic_name text, message text)", "topic_name,message");
			
			//This topic to hold all messages for topics which have not been defined.
			Topic.createInstance(ConfLoader.getInstance().getDefaultSyncLiteTableName(), ConfLoader.getInstance().getDefaultSyncLiteTableName(), 2, "create table " + ConfLoader.getInstance().getDefaultSyncLiteTableName() + "(topicName text, message text)", "topic_name,message");
			
		} catch(Exception e) {
			throw new SyncLiteException("Failed to load topicName metadata from qreader metadata file : " + this.qReaderMetadataFile, e);
		}		
	}

	@Override
	public final void run() {
		try {
			initDriver();
			globalTracer.info("Initialized READ job");
			runReadServices();
			globalTracer.info("Finished READ job");
		} catch (Exception e) {
			globalTracer.error("ERROR : ", e);
			System.out.println("ERROR : " + e.getMessage());
			System.exit(1);
		}
	}


}

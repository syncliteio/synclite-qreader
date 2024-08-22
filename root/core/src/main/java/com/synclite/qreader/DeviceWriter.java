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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import io.synclite.logger.*;

public class DeviceWriter {

	private static final int MAX_SYNCLITE_DEVICE_NAME_LENGTH = 64;
	private static ConcurrentHashMap<String, DeviceWriter> devices = new ConcurrentHashMap<String, DeviceWriter>();
	private String deviceName;	
	private Path deviceFilePath;
	private String deviceURL;
	private Logger tracer;
	private Connection deviceConn;
	private HashMap<Topic, PreparedStatement> prepStmts = new HashMap<Topic, PreparedStatement>();
	private HashSet<PreparedStatement> filledPrepStmts = new HashSet<PreparedStatement>();
	private HashSet<String> deviceTables = new HashSet<String>();
	private DeviceWriter(String deviceName, Logger tracer) throws SyncLiteException {
		try {
	        this.deviceName = deviceName.replaceAll("[^a-zA-Z0-9]", "");
			if (this.deviceName.length() > 64) {
				this.deviceName = this.deviceName.substring(0, MAX_SYNCLITE_DEVICE_NAME_LENGTH);
			}
	        this.tracer = tracer;
			this.deviceFilePath = ConfLoader.getInstance().getSyncLiteDeviceDir().resolve(this.deviceName + ".db");
			if ((ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.TELEMETRY) || (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.STREAMING)) {
				this.deviceURL = "jdbc:synclite_telemetry:" + deviceFilePath;
				Telemetry.initialize(this.deviceFilePath, ConfLoader.getInstance().getSyncLiteLoggerConfigurationFile(), deviceName);
			} else if (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.SQLITE_APPENDER) {
				this.deviceURL = "jdbc:synclite_sqlite_appender:" + deviceFilePath;
				SQLiteAppender.initialize(this.deviceFilePath, ConfLoader.getInstance().getSyncLiteLoggerConfigurationFile(), deviceName);
			} else if (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.DUCKDB_APPENDER) {
				this.deviceURL = "jdbc:synclite_duckdb_appender:" + deviceFilePath;
				DuckDBAppender.initialize(this.deviceFilePath, ConfLoader.getInstance().getSyncLiteLoggerConfigurationFile(), deviceName);
			} else if (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.DERBY_APPENDER) {
				this.deviceURL = "jdbc:synclite_derby_appender:" + deviceFilePath;
				DerbyAppender.initialize(this.deviceFilePath, ConfLoader.getInstance().getSyncLiteLoggerConfigurationFile(), deviceName);
			} else if (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.H2_APPENDER) {
				this.deviceURL = "jdbc:synclite_h2_appender:" + deviceFilePath;
				H2Appender.initialize(this.deviceFilePath, ConfLoader.getInstance().getSyncLiteLoggerConfigurationFile(), deviceName);
			} else if (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.HYPERSQL_APPENDER) {
				this.deviceURL = "jdbc:synclite_hypersql_appender:" + deviceFilePath;
				HyperSQLAppender.initialize(this.deviceFilePath, ConfLoader.getInstance().getSyncLiteLoggerConfigurationFile(), deviceName);
			} else {
				throw new SyncLiteException("Failed to create DeviceWriter for SyncLite device type : " + ConfLoader.getInstance().getSyncLiteDeviceType());
			}
			this.deviceConn = DriverManager.getConnection(deviceURL);
			loadDeviceTables();
		} catch (Exception e) {
			throw new SyncLiteException("Failed to initialize device writer for device : " + deviceName, e);
		}
	}

	private final void loadDeviceTables() throws SQLException {
		try(Statement stmt = this.deviceConn.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master")) {
				while (rs.next()) {
					deviceTables.add(rs.getString("name"));
				}
			}
		}
	}

	private PreparedStatement writeMessageImpl(Message m) throws SyncLiteException {
		try {
			Topic t = Topic.getInstance(m.topicName);
			Topic corruptMessageTopic = Topic.getInstance(ConfLoader.getInstance().getCorruptMessagesSyncLiteTableName());
			if (t == null) {				
				if (ConfLoader.getInstance().getIgnoreMessagesForUndefinedTopics()) {
					return null;
				}
				//If the topicName is not known then insert into Default Table
				Topic defaultTopic = Topic.getInstance(ConfLoader.getInstance().getDefaultSyncLiteTableName());
				//create table if it is not present 
				if (!deviceTables.contains(defaultTopic.tableName)) {
					try (Statement stmt = deviceConn.createStatement()) {
						stmt.execute(defaultTopic.createTableSql);
					}
					deviceTables.add(defaultTopic.tableName);
				}
				PreparedStatement pstmt = prepStmts.get(defaultTopic);
				if (pstmt == null) {
					pstmt = deviceConn.prepareStatement(defaultTopic.insertTableSql);
					prepStmts.put(defaultTopic, pstmt);
				}
				pstmt.setObject(1, m.deviceName);
				pstmt.setObject(2, m.topicName);
				pstmt.setObject(3, new String(m.payload));
				pstmt.addBatch();
				return pstmt;
				
			} else {
				String[] fields = Tokenizer.getInstance().tokenize(m.payload);
				if (fields.length == t.fieldCnt) {
					//create table if it is not present 
					if (!deviceTables.contains(t.tableName)) {
						try (Statement stmt = deviceConn.createStatement()) {
							stmt.execute(t.createTableSql);
						}
						deviceTables.add(t.tableName);
					}
	
					PreparedStatement pstmt = prepStmts.get(t);
					if (pstmt == null) {
						pstmt = deviceConn.prepareStatement(t.insertTableSql);
						prepStmts.put(t, pstmt);
					}
					pstmt.setObject(1, m.deviceName);
					for (int i=1; i <=fields.length; ++i) {
						pstmt.setObject(i+1, fields[i-1]);
					}
					pstmt.addBatch();
					return pstmt;			
				} else {					
					if (ConfLoader.getInstance().getIgnoreCorruptMessages()) {
						return null;
					}
					//create table if it is not present 
					if (!deviceTables.contains(corruptMessageTopic.tableName)) {
						try (Statement stmt = deviceConn.createStatement()) {
							stmt.execute(corruptMessageTopic.createTableSql);
						}
						deviceTables.add(corruptMessageTopic.tableName);
					}
					PreparedStatement pstmt = prepStmts.get(corruptMessageTopic);
					if (pstmt == null) {
						pstmt = deviceConn.prepareStatement(corruptMessageTopic.insertTableSql);
						prepStmts.put(corruptMessageTopic, pstmt);
					}
					pstmt.setObject(1, m.deviceName);
					pstmt.setObject(2, m.topicName);
					pstmt.setObject(3, new String(m.payload));
					pstmt.addBatch();
					
					return pstmt;
				}
			}
		} catch (Exception e) {
			throw new SyncLiteException("Failed to process message for device : " + this.deviceName + " for topicName : " + m.topicName, e);
		}		
	}

	private void commitMessagesImpl(PreparedStatement pstmt) throws SyncLiteException {
		try {
			pstmt.executeBatch();
		} catch(Exception e) {
			throw new SyncLiteException("Failed to commit messages : ", e);
		}
	}

	public void writeMessage(Message m) throws SyncLiteException {
		PreparedStatement pstmt = writeMessageImpl(m);
		if (pstmt != null) {
			filledPrepStmts.add(pstmt);
		}
	}
	
	public void commitMessages() throws SyncLiteException {
		try {
			for (PreparedStatement pstmt : filledPrepStmts) {
				pstmt.executeBatch();
			}
			filledPrepStmts.clear();
		} catch (Exception e) {
			throw new SyncLiteException("Failed to commit messages : ", e);
		}
	}

	public void writeAndCommitMessage(Message m) throws SyncLiteException {
		try {
			PreparedStatement pstmt = writeMessageImpl(m);
			if (pstmt != null) {
				commitMessagesImpl(pstmt);
			}
		} catch (Exception e) {
			throw new SyncLiteException("Failed to write and commit message for deviceName " + this.deviceName + " for topicName : " + m.topicName, e);
		}
	}

	public static DeviceWriter getInstance(String deviceName, Logger tracer) {
		return devices.computeIfAbsent(deviceName, s -> {  
			DeviceWriter w;
			try {
				w = new DeviceWriter(deviceName, tracer);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return w;
		});
	}

	public String getDeviceName() {
		return this.deviceName;
	}

}

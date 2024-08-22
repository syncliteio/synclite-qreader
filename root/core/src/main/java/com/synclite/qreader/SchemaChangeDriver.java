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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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


public class SchemaChangeDriver implements Runnable{
	
	private static final class InstanceHolder {
		private static SchemaChangeDriver INSTANCE = new SchemaChangeDriver();
	}

	public static SchemaChangeDriver getInstance() {
		return InstanceHolder.INSTANCE;
	}

	private Logger globalTracer;

	//ScheduledExecutorService externalCommandLoader;

	private SchemaChangeDriver() {
	}
	private final void initLogger() {
		this.globalTracer = Logger.getLogger(SchemaChangeDriver.class);    	
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
		fa.setName("FileLogger");
		fa.setFile(ConfLoader.getInstance().getSyncLiteDeviceDir().resolve("synclite_qreader.trace").toString());
		fa.setLayout(new PatternLayout("%d %-5p [%t] %m%n"));
		fa.setMaxBackupIndex(10);
		fa.setMaxFileSize("10MB"); // Set the maximum file size to 10 MB
		fa.setAppend(true);
		fa.activateOptions();
		globalTracer.addAppender(fa);
		globalTracer.info("Starting SCHEMA_CHANGE job");
	}

	public final Logger getTracer() {
		return this.globalTracer;
	}

	private final void initDriver() throws SyncLiteException {
		initLogger();
		initSyncLite();
	}

	private void runSchemaChangeServices() throws SyncLiteException {
	
		//Read and validate alter table SQLs
		//Iterate on all db files in db directory
		//Connect to each db file
		//Execute each alter table sql idempotently, report errors if any and fail job
		//Execute the sqls on the metadata file
		//Update the metadata table with latest create table sqls and field count.
		//		
		try {
			//Read and validate alter table SQLs
			Path alterTableSQLFilePath = ConfLoader.getInstance().getSyncLiteDeviceDir().resolve("synclite_qreader_alter_table.sql");
			if (!Files.exists(alterTableSQLFilePath)) {
				throw new SyncLiteException("Missing file : " + alterTableSQLFilePath);
			}
			String alterTableSQLs = Files.readString(alterTableSQLFilePath);			
			if (alterTableSQLs.isBlank()) {
				throw new SyncLiteException("No alter table SQLs specified");
			}
			alterTableSQLs = alterTableSQLs.strip();

			String[] sqls = alterTableSQLs.split(";");

			if (sqls.length == 0) {
				throw new SyncLiteException("No alter table SQLs specified");
			}

			Path metadataFilePath = ConfLoader.getInstance().getSyncLiteDeviceDir().resolve("synclite_qreader_metadata.db");
			List<Path> dbFiles = Files.walk(ConfLoader.getInstance().getSyncLiteDeviceDir()).filter(s->s.toString().endsWith(".db")).collect(Collectors.toList());
			//
			//Iterate on all db files in db directory
			//Connect to each db file
			//Execute each alter table sql idempotently, report errors if any and fail job
			//
			for (Path dbFile : dbFiles) {
				
				//Skip metadata file
				if (dbFile.getFileName().toString().equals("synclite_qreader_metadata.db")) {
					continue;
				}
				
				//Try connecting to this device file				
				String url = getSyncLiteDeviceURL(dbFile);
				try (Connection conn = DriverManager.getConnection(url)) {
					conn.setAutoCommit(false);
					try (Statement stmt = conn.createStatement()) {
						for (String sql : sqls) {
							//
							//Get the table name from sql
							//
							String[] tokens = sql.split("\\s+");
							if (tokens.length < 3) {
								throw new SyncLiteException("Invalid alter table SQL : " + sql + ", specified");
							}
							String tableName = tokens[2];
							try {
								stmt.execute(sql);
								//
								//Publish REFRESH TABLE if the device type is TELEMETRY
								//Not needed for APPENDER device since we use explicit column list specification in INSERT
								//for APPENDER device.
								//								
								if (ConfLoader.getInstance().getSyncLiteDeviceType() == SyncLiteDeviceType.TELEMETRY) {
									//Read create table sql and construct refresh table sql from it
									String refreshTableSql = null;
									try (ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'")) {
										String createTableSql = rs.getString("sql");
										refreshTableSql = Topic.getRefreshTableSql(tableName, createTableSql);
										stmt.execute(refreshTableSql);
									} catch(Exception e) {
										throw new SyncLiteException("Failed to execute refresh table SQL : " + refreshTableSql + " on device : " + dbFile + " : " + e.getMessage(), e);
									}
								}
							} catch (Exception e) {
								//Check for expected errors which may happen due to repeated execution of each type of Alter 
								//statement(idempotency)
								if (e.getMessage().contains("no such table") || 
										e.getMessage().contains("duplicate column name") || 
										e.getMessage().contains("no such column")) {
									//Ignore these errors for idempotency
								} else {
									throw new SyncLiteException("Failed to execute alter table SQL : " + sql + " on device : " + dbFile + " : " + e.getMessage(), e);
								}
							}
						}
						conn.commit();
						this.globalTracer.info("Successfully executed specified alter table SQLs on device : " + dbFile);
					} catch (Exception e) {
						throw new SyncLiteException("Failed to connect to SyncLite device file : " + dbFile, e);
					}
				}
			}
		
			//
			//Execute the sqls on the metadata file
			//Update metadata table with latest create table sqls and field count.
			//
			String url = "jdbc:sqlite:" + metadataFilePath;
			String updateTopicInfoTableSql = "UPDATE topic_info SET topic_field_count = ?, topic_create_table_sql = ?, topic_table_column_list = ? WHERE topic_table_name = ?";
			try (Connection conn = DriverManager.getConnection(url)) {
				conn.setAutoCommit(false);
				try (PreparedStatement pstmt = conn.prepareStatement(updateTopicInfoTableSql); 
					 Statement stmt = conn.createStatement()) {
					for (String sql : sqls) {
						//
						//Get the table name from sql
						//
						String[] tokens = sql.split("\\s+");
						if (tokens.length < 3) {
							throw new SyncLiteException("Invalid alter table SQL : " + sql + ", specified");
						}
						
						String tableName;
						String[] tableNameTokens = tokens[2].split("\\.");
						if (tableNameTokens.length == 2) {
							tableName = tableNameTokens[1];
						} else {
							tableName = tableNameTokens[0];
						}
						
						try {
							stmt.execute(sql);
							//Get the create table sql and column count for this table.
							String createTableSql;
							try (ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'")) {
								if (rs.next()) {
									createTableSql = rs.getString("sql");
								} else {
									throw new SyncLiteException("Create table SQL for table : " + tableName + " not found after executing alter table sql : " + sql + " : ");
								}
							} catch (Exception e) {
								throw new SyncLiteException("Failed to get create table SQL for table : " + tableName + " after executing alter table sql : " + sql + " : ", e);
							}
							
							Integer colCount = 0;
							try (ResultSet rs = stmt.executeQuery("pragma table_info('" + tableName + "')")) {
								while (rs.next()) {
									++colCount;
								} 
							} catch (Exception e) {
								throw new SyncLiteException("Failed to get column count for table : " + tableName + " after executing alter table sql : " + sql + " : " + e.getMessage(), e);
							}
							
							//Get column list
							String topicTableColumnList;
							try (ResultSet rs = stmt.executeQuery("pragma table_info('" + tableName + "')")) {
								StringBuilder topicTableColumnListBuilder = new StringBuilder();
								boolean first = true;
								while (rs.next()) {
									if (!first) {
										topicTableColumnListBuilder.append(",");
									}
									topicTableColumnListBuilder.append(rs.getString("name"));
									first = false;
								}
								topicTableColumnList = topicTableColumnListBuilder.toString();
							} catch (Exception e) {
								throw new SyncLiteException("Failed to get topic table column list : " + tableName + " after executing alter table sql : " + sql + e.getMessage(), e);
							}

							try {
								//Update topic_info table
								pstmt.setInt(1, colCount);
								pstmt.setString(2, createTableSql);
								pstmt.setString(3, topicTableColumnList);
								pstmt.setString(4, tableName);
								pstmt.execute();
							} catch(Exception e) {
								throw new SyncLiteException("Failed to persist field count and create table sql for table : " + tableName + ", in metadata file : " + metadataFilePath, e);
							}							
						} catch (Exception e) {
							throw new SyncLiteException("Failed to execute alter table SQL : " + sql + " on qreader metadata file : " + metadataFilePath + " : ", e);
						}
					}
					conn.commit();
					this.globalTracer.info("Successfully executed specified alter table SQLs on qreader metadata file : " + metadataFilePath);
				} catch (Exception e) {
					throw new SyncLiteException("Failed to connect to synclite qreader metadata file : " + metadataFilePath, e);
				}
			}
		} catch(Exception e) {
			this.globalTracer.error("Failed to execute schema change operation : ", e);
			throw new SyncLiteException(e);
		}
	}


	private final String getSyncLiteDeviceURL(Path dbFile) throws SyncLiteException {
		switch (ConfLoader.getInstance().getSyncLiteDeviceType()) {
		case TELEMETRY:
		case STREAMING:
			return "jdbc:synclite_telemetry:" + dbFile;
		case SQLITE_APPENDER:
			return "jdbc:synclite_sqlite_appender:" + dbFile;
		case DUCKDB_APPENDER:
			return "jdbc:synclite_duckdb_appender:" + dbFile;
		case H2_APPENDER:
			return "jdbc:synclite_h2_appender:" + dbFile;
		case HYPERSQL_APPENDER:
			return "jdbc:synclite_hsqldb_appender:" + dbFile;
		default:
			throw new SyncLiteException("Invalid Device Type specified in configuration file : " + ConfLoader.getInstance().getSyncLiteDeviceType()); 	
		}
	}
	
	private final void initSyncLite() throws SyncLiteException {
		try {
			Class.forName("io.synclite.logger.Telemetry");
			Class.forName("io.synclite.logger.Appender");
		} catch (ClassNotFoundException e) {
			globalTracer.error("Failed to load SyncLite logger : ", e);
			throw new SyncLiteException("Failed to load SyncLite logger : ", e);
		}
	}

	@Override
	public final void run() {
		try {
			initDriver();
			globalTracer.info("Initialized job");
			runSchemaChangeServices();
			globalTracer.info("Successfully finished SCHEMA-CHANGE job");
			System.exit(0);
		} catch (Exception e) {			
			globalTracer.error("ERROR : ", e);
			System.out.println("ERROR : " + e.getMessage());
			globalTracer.info("Failed to execute SCHEMA-CHANGE job");
			System.exit(1);
		}
	}

}

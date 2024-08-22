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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Topic {
	
	private static ConcurrentHashMap<String, Topic> topics = new ConcurrentHashMap<String, Topic>();
	public String name;
	public String tableName;
	public String corruptMsgTableName;
	public String createTableSql;
	public String refreshTableSql;
	public String insertTableSql;
	public String corruptMsgCreateTableSql;
	public long fieldCnt;
	public List<String> columnList; 
	
	private Topic(String topicName, String topicTableName, long topicFieldCnt, String createTableSql, String coumnList) {
		this.name = topicName;
		this.tableName = topicTableName;
		this.fieldCnt = topicFieldCnt;
		this.createTableSql = createTableSql;
		this.columnList = columnList;
		populateSqls();
	}
	
	
	public static Topic getInstance(String name) {
		return topics.get(name);
	}


	public static Topic createInstance(String topicName, String topicTableName, long topicFieldCnt, String createTableSql, String columnList) {
        return topics.computeIfAbsent(topicName, s -> {  
            Topic t = new Topic(topicName, topicTableName, topicFieldCnt, createTableSql, columnList);
            return t;
    });
	}

	private final void populateSqls() {
		insertTableSql = "INSERT INTO " + this.tableName;
		
		//For SyncLite appender device type, we use explicit column name specification 
		//as due to DDLs, the column list may get reordered in the device, replica and SyncLite metadata.
		//We cannot use the REFRESH TABLE solution in this case as it internally drops and recreates table with
		//the specified column sequence and we cannot drop tables in appender device as it contains data
		//
		if (isAppenderDevice()) {
			StringBuilder colListBuilder = new StringBuilder();
			boolean first = true;
			for (String colName : this.columnList) {
				if (!first) {
					colListBuilder.append(",");
				}
				colListBuilder.append(colName);
				first = false;
			}
			insertTableSql += "(" + colListBuilder.toString() + ")"; 
		}
		
		insertTableSql += " VALUES(";
		boolean first = true;
		StringBuilder valBuilder = new StringBuilder();
		for (int i =0; i < this.fieldCnt + 1; ++i) {
			if (!first) {
				valBuilder.append(" ,");
			}
			valBuilder.append("?");
			first = false;
		}
		this.insertTableSql += valBuilder.toString() + ")";
		//Add device_name column as the first column to each table
		String prefix = "(device_name TEXT, ";
		this.createTableSql = "CREATE TABLE IF NOT EXISTS " + this.tableName + prefix + this.createTableSql.substring(this.createTableSql.indexOf("(") + 1);
		this.refreshTableSql = getRefreshTableSql(this.tableName, this.createTableSql);
	}

	private boolean isAppenderDevice() {
		switch(ConfLoader.getInstance().getSyncLiteDeviceType()) {
		case STREAMING:
		case TELEMETRY:
			return false;
		default: 
			return true;
		}
	}


	public static String getRefreshTableSql(String tableName, String createTableSql) {
		return "REFRESH TABLE " + tableName + createTableSql.substring(createTableSql.indexOf("("));
	}

}

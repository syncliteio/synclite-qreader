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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
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
import javax.servlet.http.HttpSession;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.Types;

/**
 * Servlet implementation class ValidateJobConfiguration
 */
@WebServlet("/validatemqtttables")
public class ValidateMQTTTables extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Logger globalTracer;

	/**
	 * Default constructor. 
	 */
	public ValidateMQTTTables() {
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

			Path syncLiteDeviceDir = Path.of(request.getSession().getAttribute("synclite-device-dir").toString());
			Path qReaderConfPath = syncLiteDeviceDir.resolve("synclite_qreader.conf"); 
			initTracer(syncLiteDeviceDir);

			String numTablesStr = request.getParameter("num-tables");

			Integer numTables = Integer.valueOf(numTablesStr);
			
			int numEnabledTables = 0;
			for (int i=0; i < numTables; ++i) {
				String topicName = request.getParameter("topic-name-" + i).strip();
				String topicTableName = request.getParameter("topic-table-name-" + i).strip(); 
				String topicFieldCountStr = request.getParameter("topic-field-count-" + i).strip();				
				String topicCreateTableSql = request.getParameter("topic-create-table-sql-" + i).strip();

				int enableTable = 0;
				if (request.getParameter("enable-" + i) != null) {
					enableTable = 1;
					++numEnabledTables;
				}

				if (topicName.isBlank()) {
					throw new ServletException("Please specify topic name for topic entry at index : " + (i+1));					
				}

				if (topicName.isBlank()) {
					throw new ServletException("Please specify table name for topic : " + topicName);					
				}
				
				long topicFieldCount = 1L;
				if (!topicFieldCountStr.isBlank()) {
					try {
						if (Long.valueOf(topicFieldCountStr) == null) {
							throw new ServletException("Please specify a valid numeric value for field count for topic : " + topicName);
						} else if (Long.valueOf(topicFieldCountStr) <= 0) {
							throw new ServletException("Please specify a positive numeric value for field Count for topic : " + topicName);
						}
						topicFieldCount = Long.valueOf(topicFieldCountStr);
					} catch (NumberFormatException e) {
						throw new ServletException("Please specify a valid numeric value for field count for topic : " + topicName);
					}
				} else {
					throw new ServletException("Please specify field count for for topic : " + topicName);
				}

				if (topicCreateTableSql.isBlank()) {
					throw new ServletException("Please specify create table sql for topic : " + topicName);					
				}

				Path qReaderMetadataFilePath = Path.of(syncLiteDeviceDir.toString(), "synclite_qreader_metadata.db");

				//All good
				//the src table info with these details now
				//
				updateSrcTableInfoInMetadataTable(qReaderMetadataFilePath, topicName, topicTableName, topicFieldCount, topicCreateTableSql, enableTable);
			}
			
			if (numEnabledTables == 0) {
				throw new ServletException("No topics enabled. Please enable at least one topic.");
			}
			
			request.getSession().setAttribute("src-num-topics", numTables);
			request.getSession().setAttribute("num-enabled-tables", numEnabledTables);

			
			/*
			//Start mqttreader core job

			HttpSession session = request.getSession();
			String corePath = Path.of(getServletContext().getRealPath("/"), "WEB-INF", "lib").toString();

			//Get current job PID if running
			long currentJobPID = 0;
			Process jpsProc;
			if (isWindows()) {
				String javaHome = System.getenv("JAVA_HOME");			
				String scriptPath = "jps";
				if (javaHome != null) {
					scriptPath = javaHome + "\\bin\\jps";
				} else {
					scriptPath = "jps";
				}
				String[] cmdArray = {scriptPath, "-l", "-m"};
				jpsProc = Runtime.getRuntime().exec(cmdArray);
			} else {
				String javaHome = System.getenv("JAVA_HOME");			
				String scriptPath = "jps";
				if (javaHome != null) {
					scriptPath = javaHome + "/bin/jps";
				} else {
					scriptPath = "jps";
				}
				String[] cmdArray = {scriptPath, "-l", "-m"};
				jpsProc = Runtime.getRuntime().exec(cmdArray);
			}
			BufferedReader stdout = new BufferedReader(new InputStreamReader(jpsProc.getInputStream()));
			String line = stdout.readLine();
			while (line != null) {
				if (line.contains("com.synclite.qreader.Main")) {
					currentJobPID = Long.valueOf(line.split(" ")[0]);
				}
				line = stdout.readLine();
			}
			//stdout.close();

			//Kill job if found

			if(currentJobPID > 0) {
				if (isWindows()) {
					Runtime.getRuntime().exec("taskkill /F /PID " + currentJobPID);
				} else {
					Runtime.getRuntime().exec("kill -9 " + currentJobPID);
				}
			}

			//Get env variable 
			String jvmArgs = "";
			if (session.getAttribute("jvm-arguments") != null) {
				jvmArgs = session.getAttribute("jvm-arguments").toString();
			}
			//Start job again
			Process p;
			if (isWindows()) {
				String scriptName = "synclite-qreader.bat";
				String scriptPath = Path.of(corePath, scriptName).toString();
				if (!jvmArgs.isBlank()) {
					try {
						//Delete and re-create a file variables.bat under scriptPath and set the variable JVM_ARGS
						Path varFilePath = Path.of(corePath, "synclite-qreader-variables.bat");
						if (Files.exists(varFilePath)) {
							Files.delete(varFilePath);
						}
						String varString = "set \"JVM_ARGS=" + jvmArgs + "\""; 
						Files.writeString(varFilePath, varString, StandardOpenOption.CREATE);
					} catch (Exception e) {
						throw new ServletException("Failed to write jvm-arguments to synclite-qreader-variables.bat file", e);
					}
				}
				String[] cmdArray = {scriptPath.toString(), "read", "--db-dir", syncLiteDeviceDir.toString(), "--config", qReaderConfPath.toString()};
				p = Runtime.getRuntime().exec(cmdArray);

			} else {				
				String scriptName = "synclite-qreader.sh";
				Path scriptPath = Path.of(corePath, scriptName);

				if (!jvmArgs.isBlank()) {
					try {
						//Delete and re-create a file variables.sh under scriptPath and set the variable JVM_ARGS
						Path varFilePath = Path.of(corePath, "synclite-qreader-variables.sh");
						String varString = "JVM_ARGS=\"" + jvmArgs + "\"";
						if (Files.exists(varFilePath)) {
							Files.delete(varFilePath);
						}
						Files.writeString(varFilePath, varString, StandardOpenOption.CREATE);
						Set<PosixFilePermission> perms = Files.getPosixFilePermissions(varFilePath);
						perms.add(PosixFilePermission.OWNER_EXECUTE);
						Files.setPosixFilePermissions(varFilePath, perms);
					} catch (Exception e) {
						this.globalTracer.error("Failed to write jvm-arguments to synclite-qreader-variables.sh file", e);
						throw new ServletException("Failed to write jvm-arguments to synclite-qreader-variables.sh file", e);
					}
				}

				// Get the current set of script permissions
				Set<PosixFilePermission> perms = Files.getPosixFilePermissions(scriptPath);
				// Add the execute permission if it is not already set
				if (!perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
					perms.add(PosixFilePermission.OWNER_EXECUTE);
					Files.setPosixFilePermissions(scriptPath, perms);
				}

				String[] cmdArray = {scriptPath.toString(), "read", "--db-dir", syncLiteDeviceDir.toString(), "--config", qReaderConfPath.toString()};
				p = Runtime.getRuntime().exec(cmdArray);		        	
			}
			//int exitCode = p.exitValue();
			//Thread.sleep(3000);
			Thread.sleep(5000);
			boolean processStatus = p.isAlive();
			if (!processStatus) {
				BufferedReader procErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				if ((line = procErr.readLine()) != null) {
					StringBuilder errorMsg = new StringBuilder();
					int i = 0;
					do {
						errorMsg.append(line);
						errorMsg.append("\n");
						line = procErr.readLine();
						if (line == null) {
							break;
						}
						++i;
					} while (i < 5);
					this.globalTracer.error("Failed to start qreader job with exit code : " + p.exitValue() + " and errors : " + errorMsg.toString());
					throw new ServletException("Failed to start qreader job with exit code : " + p.exitValue() + " and errors : " + errorMsg.toString());
				}

				BufferedReader procOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
				if ((line = procOut.readLine()) != null) {
					StringBuilder errorMsg = new StringBuilder();
					int i = 0;
					do {
						errorMsg.append(line);
						errorMsg.append("\n");
						line = procOut.readLine();
						if (line == null) {
							break;
						}
						++i;
					} while (i < 5);

					this.globalTracer.error("Failed to start qreader job with exit code : " + p.exitValue() + " and errors : " + errorMsg.toString());
					throw new ServletException("Failed to start qreader job with exit code : " + p.exitValue() + " and errors : " + errorMsg.toString());
				}

				throw new ServletException("Failed to start qreader job with exit value : " + p.exitValue());
			}
			
			
			//System.out.println("process status " + processStatus);
			//System.out.println("process output line " + line);
			request.getSession().setAttribute("job-status","STARTED");
			request.getSession().setAttribute("job-type","SYNC");
			request.getSession().setAttribute("job-start-time",System.currentTimeMillis());
			*/
			
			//request.getRequestDispatcher("dashboard.jsp").forward(request, response);
			response.sendRedirect("confirmReadJob.jsp");
		} catch (Exception e) {
			//System.out.println("exception : " + e);
			this.globalTracer.error("Exception while processing request:", e);
			String errorMsg = e.getMessage();
			request.getRequestDispatcher("configureMQTTTables.jsp?errorMsg=" + errorMsg).forward(request, response);
			throw new ServletException(e);
		}
	}

	private void updateSrcTableInfoInMetadataTable(Path metadataFilePath, String topicName, String topicTableName, long topicFieldCount, String topicCreateTableSql, int enable) throws ServletException {
		String url = "jdbc:sqlite:" + metadataFilePath;
		String metadataTableDeleteSql = "DELETE FROM topic_info WHERE topic_name = ?";
		String metadataTableInsertSql = "INSERT INTO topic_info(topic_name, topic_table_name, topic_field_count, topic_create_table_sql, topic_table_column_list, enable) VALUES(?, ?, ?, ?, ?, ?)";
		String topicDropTableSql = "DROP TABLE IF EXISTS " + topicTableName;
		String topicTableColumnList = null;
		try (Connection conn = DriverManager.getConnection(url)){
			conn.setAutoCommit(false);
			try (Statement stmt = conn.createStatement()) {
				stmt.execute(topicDropTableSql);
				stmt.execute(topicCreateTableSql);
			} catch (SQLException e) {
				throw new ServletException("Invalid create table sql specified for topic : " + topicName);
			}
			
			try (Statement stmt = conn.createStatement()) {
				try(ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE name = '" + topicTableName + "'")) {
					if (!rs.next()) {
						throw new ServletException("Specified table name for topic does not match with the one specified in create table sql for topic : " + topicName);
					}
				} catch (Exception e) {
					throw new ServletException("Failed to get topic table name while validating : " + topicTableName + " : " + e.getMessage(), e);
				}
				try(ResultSet rs = stmt.executeQuery("SELECT * FROM " + topicTableName + " LIMIT 0")) {
		            ResultSetMetaData metaData = rs.getMetaData();
		            if (metaData.getColumnCount() != topicFieldCount) {
		            	throw new ServletException("Specified topic field count does not match with number of columns specified in create table sql for topic : " + topicName);
		            }
				} catch (Exception e) {
					throw new ServletException("Failed to get column count while validating : " + topicTableName + " : " + e.getMessage(), e);
				}
				
				//Get column list
				try (ResultSet rs = stmt.executeQuery("pragma table_info('" + topicTableName + "')")) {
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
					throw new ServletException("Failed to get topic table column list while validating : " + topicTableName + " : " + e.getMessage(), e);
				}
			}			
			
			try (PreparedStatement insertPstmt = conn.prepareStatement(metadataTableInsertSql);
					PreparedStatement deletePstmt = conn.prepareStatement(metadataTableDeleteSql);					
				) {
					deletePstmt.setString(1, topicName);
					deletePstmt.execute();
					
					insertPstmt.setString(1, topicName);
					insertPstmt.setString(2, topicTableName);
					insertPstmt.setLong(3, topicFieldCount);
					insertPstmt.setString(4, topicCreateTableSql);
					insertPstmt.setString(5, topicTableColumnList);
					insertPstmt.setInt(6, enable);				
					insertPstmt.execute();				
				}
			conn.commit();		
		} catch (Exception e) {	
			this.globalTracer.error("Failed to validate and update specified topic table sql : " + topicCreateTableSql + " for topic : " + topicName, e);
			throw new ServletException("Failed to validate and update specified topic table sql : " + topicCreateTableSql + " for topic : " + topicName, e);
		}
	}

	private final void initTracer(Path workDir) {
		this.globalTracer = Logger.getLogger(ValidateMQTTReader.class);
		if (this.globalTracer.getAppender("SyncLiteQReaderTracer") == null) {
			globalTracer.setLevel(Level.INFO);
			RollingFileAppender fa = new RollingFileAppender();
			fa.setName("SyncLiteQReaderTracer");
			fa.setFile(workDir.resolve("synclite_qreader.trace").toString());
			fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
			fa.setMaxBackupIndex(10);
			fa.setAppend(true);
			fa.activateOptions();
			globalTracer.addAppender(fa);
		}
	}

	private boolean isWindows() {
		return System.getProperty("os.name").startsWith("Windows");
	}

}



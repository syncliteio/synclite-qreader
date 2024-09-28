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
@WebServlet("/removetopicentries")
public class RemoveTopicEntries extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Logger globalTracer;

	/**
	 * Default constructor. 
	 */
	public RemoveTopicEntries() {
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

			if (request.getSession().getAttribute("synclite-device-dir") == null) {
				response.sendRedirect("syncLiteTerms.jsp");
			} else {
				Path syncLiteDeviceDir = Path.of(request.getSession().getAttribute("synclite-device-dir").toString());
				Path qReaderMetadataFilePath = Path.of(syncLiteDeviceDir.toString(), "synclite_qreader_metadata.db");

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
					throw new ServletException("SyncLite Queue Reader job is running with PID : " + currentJobPID + ". Please stop the running job and retry the operation");			
				}

				initTracer(syncLiteDeviceDir);

				String numTablesStr = request.getParameter("num-tables");

				Integer numTables = Integer.valueOf(numTablesStr);

				int numSelectedTables = 0;
				for (int i=0; i < numTables; ++i) {
					if (request.getParameter("select-" + i) != null) {
						++numSelectedTables;				
					}
				}

				if (numSelectedTables == 0) {
					throw new ServletException("No topics selected. Please select at least one topic.");
				}

				if (numSelectedTables >= numTables) {
					throw new ServletException("All topics selected for removal. At least one topic must be configured.");
				}

				for (int i=0; i < numTables; ++i) {
					if (request.getParameter("select-" + i) != null) {
						String topicName = request.getParameter("topic-name-" + i);
						String tableName = request.getParameter("table-name-" + i);
						removeTopicInfoFromMetadataTable(qReaderMetadataFilePath, topicName, tableName);
					}
				}

				readRevisedTopicCounts(request, qReaderMetadataFilePath);

				response.sendRedirect("removeTopicEntries.jsp");
			}
		} catch (Exception e) {
			//System.out.println("exception : " + e);
			this.globalTracer.error("Exception while processing request:", e);
			String errorMsg = e.getMessage();
			request.getRequestDispatcher("removeTopicEntries.jsp?errorMsg=" + errorMsg).forward(request, response);
		}
	}

	private void removeTopicInfoFromMetadataTable(Path metadataFilePath, String topicName, String tableName) throws ServletException {
		String url = "jdbc:sqlite:" + metadataFilePath;
		String metadataTableDeleteSql = "DELETE FROM topic_info WHERE topic_name = '" + topicName + "'";
		String dropTopicTableSql = "DROP TABLE IF EXISTS " + tableName;
		try (Connection conn = DriverManager.getConnection(url)){
			conn.setAutoCommit(false);
			try (Statement stmt = conn.createStatement()) {
				stmt.execute(dropTopicTableSql);
				stmt.execute(metadataTableDeleteSql);
			}
			conn.commit();
		} catch (Exception e) {
			throw new ServletException("Failed to delete topic entry for topic : " + topicName, e);
		}
	}

	private void readRevisedTopicCounts(HttpServletRequest request, Path metadataFilePath) throws ServletException {
		String url = "jdbc:sqlite:" + metadataFilePath;
		String selectTotalSql = "SELECT count(*) FROM topic_info";
		String selectEnabledSql = "SELECT count(*) FROM topic_info WHERE enable = 1";

		Integer numTables = 0;
		Integer numEnabledTables = 0;
		try (Connection conn = DriverManager.getConnection(url)){
			try (Statement stmt = conn.createStatement()) {
				try (ResultSet rs = stmt.executeQuery(selectTotalSql)) {
					if (rs.next()) {
						numTables = rs.getInt(1);
					}
				}
				try (ResultSet rs = stmt.executeQuery(selectEnabledSql)) {
					if (rs.next()) {
						numEnabledTables = rs.getInt(1);
					}
				}				
			}
		} catch (Exception e) {
			throw new ServletException("Failed to read topic counts from metadata file : " + e);
		}

		request.getSession().setAttribute("src-num-topics", numTables);
		request.getSession().setAttribute("num-enabled-tables", numEnabledTables);
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



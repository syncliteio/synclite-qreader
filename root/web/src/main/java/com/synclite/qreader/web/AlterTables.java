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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/**
 * Servlet implementation class StartJob
 */
@WebServlet("/altertables")
public class AlterTables extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Logger globalTracer;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public AlterTables() {
		super();
		// TODO Auto-generated constructor stub
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			if (request.getSession().getAttribute("synclite-device-dir") == null) {
				response.sendRedirect("syncLiteTerms.jsp");
			} else {
				String corePath = Path.of(getServletContext().getRealPath("/"), "WEB-INF", "lib").toString();
				String syncLiteDeviceDir = request.getSession().getAttribute("synclite-device-dir").toString();
				String propsPath = Path.of(syncLiteDeviceDir, "synclite_qreader.conf").toString();

				initTracer(Path.of(syncLiteDeviceDir));

				//Validate alter table sqls

				String alterTableSQLs = request.getParameter("alter-table-sqls");

				if (alterTableSQLs == null) {
					throw new ServletException("Please enter Alter Table SQLs");
				} else {
					alterTableSQLs = alterTableSQLs.strip();
					if (alterTableSQLs.isBlank()) {
						throw new ServletException("Please enter Alter Table SQLs");
					}
				}
				
				Path qReaderMetadataFilePath = Path.of(syncLiteDeviceDir, "synclite_qreader_metadata.db");
				Path qReaderAlterTableSQLsFilePath = Path.of(syncLiteDeviceDir, "synclite_qreader_alter_table.sql");
				validateAlterTableSQLs(qReaderMetadataFilePath, alterTableSQLs);				

				try {
					Files.writeString(qReaderAlterTableSQLsFilePath, alterTableSQLs);
				} catch (Exception e) {
					this.globalTracer.error("Failed to write specified alter table SQLs to the file : " + qReaderAlterTableSQLsFilePath + " : " + e.getMessage(), e);
					throw new ServletException("Failed to write specified alter table SQLs to the file : " + qReaderAlterTableSQLsFilePath + " : " + e.getMessage(), e);
				}
				
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
					if (line.contains("com.synclite.qreader.Main") && line.contains(syncLiteDeviceDir)) {
						currentJobPID = Long.valueOf(line.split(" ")[0]);
					}
					line = stdout.readLine();
				}
				//Start if the job is not found
				if(currentJobPID == 0) {
					//Get env variable 
					String jvmArgs = "";
					if (request.getSession().getAttribute("jvm-arguments") != null) {
						jvmArgs = request.getSession().getAttribute("jvm-arguments").toString();
					}	
					Process p;
					if (isWindows()) {
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
								throw new ServletException("Failed to write jvm-arguments to synclite-qreader-variables.bat file : " + e.getMessage(), e);
							}
						}
						String scriptName = "synclite-qreader.bat";
						String scriptPath = Path.of(corePath, scriptName).toString();
						String[] cmdArray = {scriptPath, "schema-change", "--db-dir", syncLiteDeviceDir, "--config", propsPath};
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
								this.globalTracer.error("Failed to write jvm-arguments to synclite-qreader-variables.sh file :" + e.getMessage(), e);
								throw new ServletException("Failed to write jvm-arguments to synclite-qreader-variables.sh file : " + e.getMessage(), e);
							}
						}

						// Get the current set of script permissions
						Set<PosixFilePermission> perms = Files.getPosixFilePermissions(scriptPath);
						// Add the execute permission if it is not already set
						if (!perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
							perms.add(PosixFilePermission.OWNER_EXECUTE);
							Files.setPosixFilePermissions(scriptPath, perms);
						}

						String[] cmdArray = {scriptPath.toString(), "schema-change", "--db-dir", syncLiteDeviceDir, "--config", propsPath};
						p = Runtime.getRuntime().exec(cmdArray);					
					}
					request.getSession().setAttribute("job-status","STARTED");
					request.getSession().setAttribute("job-type","SCHEMA-CHANGE");
					request.getSession().setAttribute("job-start-time",System.currentTimeMillis());
					request.getRequestDispatcher("dashboard.jsp").forward(request, response);
				}
				else {
					String errorMsg = "A SyncLite Qreader job with process ID : " + currentJobPID + " is running. Please stop the job and retry.";
					request.getRequestDispatcher("alterTables.jsp?errorMsg=" + errorMsg).forward(request, response);
				}
			}
		} catch (Exception e) {
			String errorMsg = e.getMessage();
			this.globalTracer.error("Failed to submit alter table job : ", e);
			request.getRequestDispatcher("alterTables.jsp?errorMsg=" + errorMsg).forward(request, response);
		}
	}

	private void validateAlterTableSQLs(Path metadataFilePath, String alterTableSQLs) throws ServletException {
		String url = "jdbc:sqlite:" + metadataFilePath;
		String[] sqls = alterTableSQLs.split(";");
		try (Connection conn = DriverManager.getConnection(url)){
			conn.setAutoCommit(false);
			try (Statement stmt = conn.createStatement()) {
				for (String sql : sqls) {
					try {
						stmt.execute(sql);
					} catch (Exception e) {
						this.globalTracer.error("Specified Alter Table SQL : " + sql + " fails with exception : " + e.getMessage(), e);
						throw new ServletException("Specified Alter Table SQL : " + sql + " fails with exception : " + e.getMessage(), e);
					}
				}
			}
			conn.rollback();
		} catch(Exception e) {
			throw new ServletException(e);
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	private boolean isWindows() {
		return System.getProperty("os.name").startsWith("Windows");
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

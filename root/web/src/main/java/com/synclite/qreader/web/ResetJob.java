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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

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
 * Servlet implementation class ValidateJobConfiguration
 */
@WebServlet("/resetJob")
public class ResetJob extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	private Logger globalTracer;

	/**
	 * Default constructor. 
	 */
	public ResetJob() {
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
			String jobName = request.getSession().getAttribute("job-name").toString();

			initTracer(syncLiteDeviceDir);

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
				if (line.contains("com.synclite.qreader.Main") && line.contains(syncLiteDeviceDir.toString())) {
					currentJobPID = Long.valueOf(line.split(" ")[0]);
				}
				line = stdout.readLine();
			}
			if(currentJobPID != 0) {
				String errorMessage = "A job is already running with Process ID : " + currentJobPID + ". Please stop the job and then run Reset Job";
				request.getRequestDispatcher("resetJob.jsp?errorMsg=" + errorMessage).forward(request, response);
			} else {
				this.globalTracer.info("Starting to reset Job : " + jobName + " under job directory : " + syncLiteDeviceDir);

				String keepJobConfiguration = request.getParameter("qreader-keep-job-configuration");
				String keepTopicConfiguration = request.getParameter("qreader-keep-topic-configuration");

				//Delete db directory contents except config file and metadata file (as chosen)
				//Delete commandDir contents
				//Delete stageDir contents

				Path dbDir = syncLiteDeviceDir;
				Path stageDir = Path.of(System.getProperty("user.home"), "synclite", jobName, "stageDir");
				Path commandDir = Path.of(System.getProperty("user.home"), "synclite", jobName, "commandDir");
				Path loggerConfigFile = Path.of(request.getSession().getAttribute("synclite-logger-configuration-file").toString());

				List<String> excludeFilesInDBDir = new ArrayList<String>();
				excludeFilesInDBDir.add("synclite_qreader.trace");
				excludeFilesInDBDir.add(loggerConfigFile.getFileName().toString());
				if (keepJobConfiguration.equals("true")) {
					excludeFilesInDBDir.add("synclite_qreader.conf");
					excludeFilesInDBDir.add("synclite_qreader_scheduler.conf");
				}
				if (keepTopicConfiguration.equals("true")) {
					excludeFilesInDBDir.add("synclite_qreader_metadata.db");
				}

				if (Files.exists(dbDir)) {
					this.globalTracer.info("Deleting files in : " + dbDir);
					try {
						Files.walkFileTree(dbDir, new SimpleFileVisitor<>() {
							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
								try {	                    	
									if (! excludeFilesInDBDir.contains(file.getFileName().toString())) { // Exclude files with specific name
										if (Files.exists(file)) {
											Files.delete(file);
										}
									} 
								} catch (IOException e) {
									throw new IOException("Failed to delete file : " + file, e);
								}
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
								try {
									if (dir.equals(dbDir)) {
										return FileVisitResult.CONTINUE;
									}

									if (Files.exists(dir)) {			            	
										Files.delete(dir);
									}
									return FileVisitResult.CONTINUE;
								} catch (IOException e) {
									throw new IOException("Failed to delete directory : " + dir, e);
								}
							}
						});
					} catch (IOException e) {
						this.globalTracer.error("Failed to delete files in directory : " + dbDir + " : " + e.getMessage(), e);
						throw new ServletException("Failed to delete files in directory : " + dbDir + " : " + e.getMessage(), e);
					}
					this.globalTracer.info("Deleted files in : " + dbDir);
				}
				
				if (Files.exists(stageDir)) {
					this.globalTracer.info("Deleting files in : " + stageDir);
					try {
						Files.walkFileTree(stageDir, new SimpleFileVisitor<>() {
							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
								try {
									if (Files.exists(file)) {
										Files.delete(file);
									} 
								} catch (IOException e) {
									throw new IOException("Failed to delete file : " + file, e);
								}
								return FileVisitResult.CONTINUE;
							}

							@Override
							public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
								try {
									if (dir.equals(stageDir)) {
										return FileVisitResult.CONTINUE;
									}

									if (Files.exists(dir)) {			            	
										Files.delete(dir);
									}
									return FileVisitResult.CONTINUE;
								} catch (IOException e) {
									throw new IOException("Failed to delete directory : " + dir, e);
								}
							}

						});
					} catch (IOException e) {
						this.globalTracer.error("Failed to delete files in directory : " + stageDir + " : " + e.getMessage(), e);
						throw new ServletException("Failed to delete files in directory : " + stageDir + " : " + e.getMessage(), e);
					}
					this.globalTracer.info("Deleted files in : " + stageDir);
				}

				if (Files.exists(commandDir)) {
					this.globalTracer.info("Deleting files in : " + commandDir);
					try {
						Files.walkFileTree(commandDir, new SimpleFileVisitor<>() {
							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
								try {
									if (Files.exists(file)) {
										Files.delete(file);
									} 
								} catch (IOException e) {
									throw new IOException("Failed to delete file : " + file, e);
								}
								return FileVisitResult.CONTINUE;
							}
	
						       @Override
						        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						            try {
						                if (dir.equals(commandDir)) {
						                    return FileVisitResult.CONTINUE;
						                }
						                
										if (Files.exists(dir)) {			            	
											Files.delete(dir);
										}
						                return FileVisitResult.CONTINUE;
						            } catch (IOException e) {
						                throw new IOException("Failed to delete directory : " + dir, e);
						            }
						        }
						});
					} catch (IOException e) {
						this.globalTracer.error("Failed to delete files in directory : " + commandDir + " : " + e.getMessage(), e);
						throw new ServletException("Failed to delete files in directory : " + commandDir + " : " + e.getMessage(), e);
					}
					this.globalTracer.info("Deleted files in : " + commandDir);
				}
				this.globalTracer.info("Finished resetting Job : " + jobName + " under job directory : " + syncLiteDeviceDir);
				
				response.sendRedirect("syncLiteTerms.jsp");
			}
		} catch (Exception e) {
			//System.out.println("exception : " + e);
			String errorMsg = e.getMessage();
			request.getRequestDispatcher("resetJob.jsp?errorMsg=" + errorMsg).forward(request, response);
			throw new ServletException(e);
		}
	}
	
	private final void initTracer(Path workDir) {
		this.globalTracer = Logger.getLogger(ResetJob.class);    	
		globalTracer.setLevel(Level.INFO);
		if (this.globalTracer.getAppender("SyncLiteQReaderTracer") == null) {
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

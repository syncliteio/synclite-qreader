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
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

/**
 * Servlet implementation class SaveJobConfiguration
 */
@WebServlet("/loadJob")
public class LoadJob extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor. 
	 */
	public LoadJob() {
		super();
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			String syncLiteDeviceDir = request.getParameter("synclite-device-dir");

			Path syncLiteDeviceDirPath;
			if ((syncLiteDeviceDir == null) || syncLiteDeviceDir.trim().isEmpty()) {
				throw new ServletException("\"SyncLite Device Directory\" must be specified");
			} else {
				syncLiteDeviceDirPath = Path.of(syncLiteDeviceDir);
				if (! Files.exists(syncLiteDeviceDirPath)) {
					throw new ServletException("Specified \"SyncLite Device Directory\" : " + syncLiteDeviceDir + " does not exist, please specify a valid \"SyncLite Device Directory\"");
				}
			}

			if (! syncLiteDeviceDirPath.toFile().canRead()) {
				throw new ServletException("Specified \"SyncLite Device Directory\" does not have read permission");
			}

			if (! syncLiteDeviceDirPath.toFile().canWrite()) {
				throw new ServletException("Specified \"SyncLite Device Directory\" does not have write permission");
			}

			Path configPath = syncLiteDeviceDirPath.resolve("synclite_qreader.conf");
			if (!Files.exists(configPath)) {
				throw new ServletException("Synclite QReader Configuration file does not exist in specified \"SyncLite Device Directory\". Please configure the job and start if it is was not previous configured and run.");
			}

			loadQReaderConfig(request, configPath);

			setAdditionalSessionVariables(syncLiteDeviceDir, request);

			//request.getRequestDispatcher("dashboard.jsp").forward(request, response);
			response.sendRedirect("dashboard.jsp");
		} catch (Exception e) {
			//		request.setAttribute("saveStatus", "FAIL");
			System.out.println("exception : " + e);
			String errorMsg = e.getMessage();
			request.getRequestDispatcher("loadJob.jsp?errorMsg=" + errorMsg).forward(request, response);
		}
	}

	private void setAdditionalSessionVariables(String syncLiteDeviceDir, HttpServletRequest request) throws ServletException {
		
		Path qReaderMetadataFilePath = Path.of(syncLiteDeviceDir, "synclite_qreader_metadata.db");
		
		if (!Files.exists(qReaderMetadataFilePath)) {
			throw new ServletException("Queue Reader job metadata file missing : " + qReaderMetadataFilePath);
		}
		
		//Read num-enabled-tables
		try {
			Class.forName("org.sqlite.JDBC");
			try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + qReaderMetadataFilePath)) {
				try (Statement stmt = conn.createStatement()) {
					try(ResultSet rs = stmt.executeQuery("SELECT count(*) FROM topic_info")) {
						request.getSession().setAttribute("src-num-topics", rs.getObject(1));						
					}					
					try(ResultSet rs = stmt.executeQuery("SELECT count(*) FROM topic_info WHERE enable = 1")) {
						request.getSession().setAttribute("num-enabled-tables", rs.getObject(1));						
					}
				}
			}
		} catch (Exception e) {
			throw new ServletException("Failed to read DB Reader job metadata file : " + qReaderMetadataFilePath, e);
		}

		//Set dst-type-name-<dstIndex> 		
		request.getSession().setAttribute("job-status","STOPPED");
		request.getSession().setAttribute("job-type","READ");
		//TODO fix start time for job being loaded
		request.getSession().setAttribute("job-start-time",System.currentTimeMillis());
	}

	private final void loadQReaderConfig(HttpServletRequest request, Path propsPath) throws ServletException {
		//HashMap properties = new HashMap<String, String>();
		BufferedReader reader = null;
		try {
			if (Files.exists(propsPath)) {		
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
					String[] tokens = line.split("=");
					if (tokens.length < 2) {
						if (tokens.length == 1) {
							if (!line.startsWith("=")) {								
								request.getSession().setAttribute(tokens[0].trim().toLowerCase(), line.substring(line.indexOf("=") + 1, line.length()).trim());
							}
						}
					}  else {
						request.getSession().setAttribute(tokens[0].trim().toLowerCase(), line.substring(line.indexOf("=") + 1, line.length()).trim());	
					}					
					line = reader.readLine();
				}
				reader.close();
			}
		} catch (Exception e){ 
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e1) {
				//Ignore
			}
			throw new ServletException("Failed to load qreader config file : " + propsPath, e);
		} 
	}

	/**	  
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
}

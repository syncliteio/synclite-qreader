<%-- 
    Copyright (c) 2024 mahendra.chavan@syncLite.io, all rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
    in compliance with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License
    is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
    or implied.  See the License for the specific language governing permissions and limitations
    under the License.
--%>

<%@page import="java.time.ZoneId"%>
<%@page import="java.time.LocalDateTime"%>
<%@page import="java.time.ZonedDateTime"%>
<%@page import="java.time.Instant"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.io.BufferedReader"%>
<%@page import="java.io.InputStreamReader"%>
<%@page import="java.nio.file.Files"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ page import="java.sql.*"%>
<%@ page import="org.sqlite.*"%>
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href=css/SyncLiteStyle.css>
<title>SyncLite Queue Reader Dashboard</title>

<script type="text/javascript">

function autoRefreshSetTimeout() {
    const refreshInterval = parseInt(document.getElementById("refresh-interval").value);
    
    if (!isNaN(refreshInterval)) {
    	const val = refreshInterval * 1000;
    	if (val === 0) {
    		const timeoutObj = setTimeout("autoRefresh()", 1000);
    		clearTimeout(timeoutObj);    		
    	} else {    		
    		setTimeout("autoRefresh()", val);
    	}
	}	
}

function autoRefresh() {
	document.forms['dashboardForm'].submit();
}

</script>

</head>

<body onload="autoRefreshSetTimeout()">
	<%@include file="html/menu.html"%>	
	<div class="main">
		<h2>SyncLite Queue Reader Dashboard</h2>
		<%
			if ((session.getAttribute("job-status") == null) || (session.getAttribute("synclite-device-dir") == null)) {
				out.println("<h4 style=\"color: red;\"> Please configure and start/load a QReader job.</h4>");
				throw new javax.servlet.jsp.SkipPageException();
			}		
		
		
			Path readerMetadataDBPath = Path.of(session.getAttribute("synclite-device-dir").toString(), "synclite_qreader_metadata.db");		
			if (!Files.exists(readerMetadataDBPath)) {
				out.println("<h4 style=\"color: red;\"> Metadata file for the QReader job is missing .</h4>");
				throw new javax.servlet.jsp.SkipPageException();				
			}		
		%>

		<center>
			<%-- response.setIntHeader("Refresh", 2); --%>
			<table>
				<tbody>
					<%	
					String syncliteDeviceDir = session.getAttribute("synclite-device-dir").toString();
					int refreshInterval = 5;
					if (request.getParameter("refresh-interval") != null) {
						try {
							refreshInterval = Integer.valueOf(request.getParameter("refresh-interval").toString());
						} catch (Exception e) {
							refreshInterval = 5;
						}
					}

					//Get current job PID if running
					long currentJobPID = 0;
					{
						Process jpsProc;
						if (! System.getProperty("os.name").startsWith("Windows")) {
							String javaHome = System.getenv("JAVA_HOME");			
							String scriptPath = "jps";
							if (javaHome != null) {
								scriptPath = javaHome + "/bin/jps";
							} else {
								scriptPath = "jps";
							}
							String[] cmdArray = {scriptPath, "-l", "-m"};
							jpsProc = Runtime.getRuntime().exec(cmdArray);
						} else {
							String javaHome = System.getenv("JAVA_HOME");			
							String scriptPath = "jps";
							if (javaHome != null) {
								scriptPath = javaHome + "\\bin\\jps";
							} else {
								scriptPath = "jps";
							}
							String[] cmdArray = {scriptPath, "-l", "-m"};
							jpsProc = Runtime.getRuntime().exec(cmdArray);
						}						
						BufferedReader stdout = new BufferedReader(new InputStreamReader(jpsProc.getInputStream()));
						String line = stdout.readLine();
						while (line != null) {
							if (line.contains("com.synclite.qreader.Main") && line.contains(syncliteDeviceDir)) {
								currentJobPID = Long.valueOf(line.split(" ")[0]);
							}
							line = stdout.readLine();
						}						
					}

					String jobName = "UNKNOWN";
					if (session.getAttribute("job-name") != null) {
						jobName = session.getAttribute("job-name").toString();
					}

                	String lastKnownJobProcessStatus = "RUNNING";
                	if (currentJobPID == 0) {
                		lastKnownJobProcessStatus = "STOPPED";
                	}

					out.println("<table>");
                	out.println("<tr>");
                	out.println("<td></td>");
                	out.println("<td>");
                	out.println("<form name=\"dashboardForm\" method=\"post\" action=\"dashboard.jsp\">");
                	out.println("<div class=\"pagination\">");
                	out.println("REFRESH IN ");
                	out.println("<input type=\"text\" id=\"refresh-interval\" name=\"refresh-interval\" value =\"" + refreshInterval + "\" size=\"1\" onchange=\"autoRefreshSetTimeout()\">");
                	out.println(" SECONDS");
                	out.println("</div>");
                	out.println("</form>");
                	out.println("</td>");
                	out.println("</tr>");

                	out.println("<tr>");
                	out.println("<td> QReader Job Name </td>");
                	out.println("<td>" + jobName + "</td>");
                	out.println("</tr>");

                	out.println("<tr>");
                	out.println("<td> Job Process Status </td>");
                	out.println("<td><a href=\"jobTrace.jsp\">" + lastKnownJobProcessStatus + "</a></td>");
                	out.println("</tr>");
                   	out.println("<tr>");
                	out.println("<td> Job Process ID </td>");
                	out.println("<td>"+ currentJobPID + "</td>");
                	out.println("</tr>");
                	if(session.getAttribute("job-type") != null) {
	                	out.println("<tr>");
	                	out.println("<td> Job Type </td>");
	                	out.println("<td>"+ session.getAttribute("job-type").toString() + "</td>");
	                	out.println("</tr>");
                	}
                	out.println("<tr>");
                	out.println("<td> Number of Topics</td>");
                	out.println("<td>"+ request.getSession().getAttribute("num-enabled-tables") + "</td>");
                	out.println("</tr>");
                	out.println("<tr>");
                    out.println("<td> Job Elapsed Time </td>");               
                    String elapsedTimeStr = "0 Seconds";
                    if (session.getAttribute("job-status").toString().equals("STARTED") && (session.getAttribute("job-start-time") != null)) {
                    	long jobStartTime = (long) session.getAttribute("job-start-time");
                    	long elapsedTime = (System.currentTimeMillis() - jobStartTime) / 1000L ;
                    	long elapsedTimeDays = 0L;
	                    long elapsedTimeHours = 0L;
	                    long elapsedTimeMinutes = 0L;
	                    long elapsedTimeSeconds = 0L;

	                    elapsedTimeStr = "";
                    	if (elapsedTime > 86400) {
                    		elapsedTimeDays = elapsedTime / 86400;
                    		if (elapsedTimeDays > 0) {
                    			if (elapsedTimeDays == 1) {
                    				elapsedTimeStr = elapsedTimeStr + elapsedTimeDays + " Day ";
                    			} else {
                    				elapsedTimeStr = elapsedTimeStr + elapsedTimeDays + " Days ";
                    			}
                    		}
                    	}

                    	if (elapsedTime > 3600) {
                    		elapsedTimeHours= (elapsedTime % 86400) / 3600;
                    		if (elapsedTimeHours > 0) {
                    			if (elapsedTimeHours == 1) {
                    				elapsedTimeStr = elapsedTimeStr + elapsedTimeHours + " Hour ";
                    			} else {
                    				elapsedTimeStr = elapsedTimeStr + elapsedTimeHours + " Hours ";
                    			}
                    		}
                    	}
                    	
                    	if (elapsedTime > 60) {
                    		elapsedTimeMinutes = (elapsedTime % 3600) / 60;
                    		if (elapsedTimeMinutes > 0) {
                    			if (elapsedTimeMinutes == 1) {
                    				elapsedTimeStr = elapsedTimeStr + elapsedTimeMinutes + " Minute ";	
                    			} else {
                    				elapsedTimeStr = elapsedTimeStr + elapsedTimeMinutes + " Minutes ";
                    			}
                    			
                    		}
                    	}	                    	
                    	elapsedTimeSeconds = elapsedTime % 60;
						if (elapsedTimeSeconds == 1) {
							elapsedTimeStr = elapsedTimeStr + elapsedTimeSeconds + " Second";
						} else {
                    		elapsedTimeStr = elapsedTimeStr + elapsedTimeSeconds + " Seconds";
						}
                    }
                    out.println("<td>" +  elapsedTimeStr + "</td>");
                    out.println("</tr>");

                	out.println("</table>");
            	%>
				</tbody>
			</table>
		</center>
	</div>
</body>
</html>
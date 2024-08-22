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

<%@page import="java.nio.file.Files"%>
<%@page import="java.time.Instant"%>
<%@page import="java.io.File"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.io.BufferedReader"%>
<%@page import="java.io.FileReader"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.HashMap"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ page import="java.sql.*"%>
<%@ page import="org.sqlite.*"%>
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href=css/SyncLiteStyle.css>

<title>View/Remove Topic Entries</title>
</head>
<body>
	<%@include file="html/menu.html"%>	

	<div class="main">
		<h2>View/Remove Topic Configurations</h2>
		Note: Please stop the job and scheduler if running before executing this operation.<br>
		<%
			
			if ((session.getAttribute("job-status") == null) || (session.getAttribute("synclite-device-dir") == null)) {
				out.println("<h4 style=\"color: red;\"> Please configure and start/load a QReader job.</h4>");
				throw new javax.servlet.jsp.SkipPageException();		
			}
		
			if (session.getAttribute("syncite-qreader-job-starter-scheduler") != null) {
				out.println("<h4 style=\"color: red;\"> Please stop the QReader job scheduler to proceed with this operation.</h4>");
				throw new javax.servlet.jsp.SkipPageException();		
			}
		
			Path readerMetadataDBPath = Path.of(session.getAttribute("synclite-device-dir").toString(), "synclite_qreader_metadata.db");		
			if (!Files.exists(readerMetadataDBPath)) {
				out.println("<h4 style=\"color: red;\"> Metadata file for the Queue Reader job is missing .</h4>");
				throw new javax.servlet.jsp.SkipPageException();				
			}		
		%>
		
		<%
		String errorMsg = request.getParameter("errorMsg");
		Integer numTopics = Integer.valueOf(session.getAttribute("src-num-topics").toString());
		%>

		<%
		if (errorMsg != null) {
			out.println("<h4 style=\"color: red;\">" + errorMsg + "</h4>");
		}
		%>

		<form action="${pageContext.request.contextPath}/removetopicentries"	method="post">
			<table>
				<tbody>
				<tr></tr>
				<tr>
					<th>Select <input type="checkbox" id="select-all" name="select-all"></th>
					<th>Topic Name</th>
					<th>Table Name</th>
					<th>Field Count</th>
					<th>Create Table SQL</th>
					<th>Enabled</th>
				</tr>
				<%
					Path metdataFilePath = Path.of(session.getAttribute("synclite-device-dir").toString(), "synclite_qreader_metadata.db");					
					Class.forName("org.sqlite.JDBC");
					int idx = 0;
					try(Connection conn = DriverManager.getConnection("jdbc:sqlite:" + metdataFilePath)) {
						try(Statement stat = conn.createStatement()){					
							try(ResultSet rs = stat.executeQuery("select topic_name, topic_table_name, topic_field_count, topic_create_table_sql, enable from topic_info order by topic_name")) {
								while (rs.next()) {
									out.println("<tr>");									
									if (request.getParameter("topic-name-" + idx) != null) {
										if (request.getParameter("select-" + idx) != null) {
											out.println("<td><input type=\"checkbox\" id=\"select-" + idx + "\" name=\"select-" + idx + "\" value=\"" + "1" + "\" checked/></td>");					
										} else {
											out.println("<td><input type=\"checkbox\" id=\"select-" + idx + "\" name=\"select-" + idx + "\" value=\"" + "0" + "\"/></td>");	
										}
									} else {										
										out.println("<td><input type=\"checkbox\" id=\"select-" + idx + "\" name=\"select-" + idx + "\" value=\"" + "0" + "\" /></td>");
									}
									String topicName = rs.getString("topic_name");
									out.println("<td>" + topicName + "</td>");
									out.println("<input type=\"hidden\" id=\"topic-name-" + idx + "\" name=\"topic-name-" + idx + "\" value=\"" + topicName + "\" />");

									String tableName = rs.getString("topic_table_name");
									out.println("<td>" + tableName + "</td>");
									out.println("<input type=\"hidden\" id=\"table-name-" + idx + "\" name=\"table-name-" + idx + "\" value=\"" + tableName + "\" />");

									String topicFieldCount = rs.getString("topic_field_count");
									out.println("<td>" + topicFieldCount + "</td>");

									String topicCreateTableSql = rs.getString("topic_create_table_sql");
									out.println("<td>" + topicCreateTableSql + "</td>");

									String enable = (rs.getInt("enable") == 1) ? "true" : "false";
									out.println("<td>" + enable + "</td>");
									
									out.println("</tr>");
									++idx;
								}
							}
						}
					}
					out.println("<input type=\"hidden\" id=\"num-tables\" name=\"num-tables\" value=\"" + idx + "\"/>");
					%>
				</tbody>
			</table>
			<center>
				<button type="submit" name="remove">Remove</button>
			</center>			
		</form>
	</div>
	
<script type="text/javascript">
	// Get references to the checkboxes
	const selectAllCheckbox = document.getElementById("select-all");
	const individualCheckboxes = document.querySelectorAll('input[type="checkbox"][name^="select-"]');
	
	// Add an event listener to the "select-all" checkbox
	selectAllCheckbox.addEventListener("change", function() {
	  const isChecked = selectAllCheckbox.checked;
	  // Set the state of individual checkboxes to match the "select-all" checkbox
	  individualCheckboxes.forEach(function(checkbox) {
	    checkbox.checked = isChecked;
	  });
	});
</script>
	
</body>
</html>
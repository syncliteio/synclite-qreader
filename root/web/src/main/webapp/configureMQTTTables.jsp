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

<title>Configure Topic Schemas</title>
</head>


<%
String errorMsg = request.getParameter("errorMsg");

if (session.getAttribute("synclite-device-dir") == null) {
	response.sendRedirect("syncLiteTerms.jsp");
}

Integer numTopics = Integer.valueOf(session.getAttribute("src-num-topics").toString());
%>

<body>
	<%@include file="html/menu.html"%>	

	<div class="main">
		<h2>Configure Topics</h2>
		<%
		if (errorMsg != null) {
			out.println("<h4 style=\"color: red;\">" + errorMsg + "</h4>");
		}
		%>

		<form action="${pageContext.request.contextPath}/validatemqtttables"	method="post">
			<table>
				<tbody>
				<tr></tr>
				<tr>
					<th>Enable <input type="checkbox" id="enable-all" name="enable-all"></th>
					<th>Topic Name</th>
					<th>Table Name</th>
					<th>Field Count</th>
					<th>Create Table SQL</th>
				</tr>
				<%
					Path metdataFilePath = Path.of(session.getAttribute("synclite-device-dir").toString(), "synclite_qreader_metadata.db");					
					Class.forName("org.sqlite.JDBC");
					int idx = 0;
					try(Connection conn = DriverManager.getConnection("jdbc:sqlite:" + metdataFilePath)) {
						try(Statement stat = conn.createStatement()){					
							try(ResultSet rs = stat.executeQuery("select topic_name, topic_table_name, topic_field_count, topic_create_table_sql, enable from topic_info")) {
								while (rs.next()) {
									out.println("<tr>");
									if (request.getParameter("topic-name-" + idx) != null) {
										if (request.getParameter("enable-" + idx) != null) {
											out.println("<td><input type=\"checkbox\" id=\"enable-" + idx + "\" name=\"enable-" + idx + "\" value=\"" + "1" + "\" checked/></td>");					
										} else {
											out.println("<td><input type=\"checkbox\" id=\"enable-" + idx + "\" name=\"enable-" + idx + "\" value=\"" + "0" + "\"/></td>");	
										}
									} else {
										if (rs.getInt("enable") == 1) {
											out.println("<td><input type=\"checkbox\" id=\"enable-" + idx + "\" name=\"enable-" + idx + "\" value=\"" + rs.getInt("enable") + "\" checked/></td>");							
										} else {
											out.println("<td><input type=\"checkbox\" id=\"enable-" + idx + "\" name=\"enable-" + idx + "\" value=\"" + rs.getInt("enable") + "\" /></td>");
										}
									}
									
									if (request.getParameter("topic-name-" + idx) != null) {
										String val = request.getParameter("topic-name-" + idx);
										out.println("<td><input type=\"text\" id=\"topic-name-" + idx + "\" name=\"topic-name-" + idx + "\" value=\"" + val + "\" title=\"Specify topic name\" readonly/></td>");
									} else {
										out.println("<td><input type=\"text\" id=\"topic-name-" + idx + "\" name=\"topic-name-" + idx + "\" value=\"" + rs.getString("topic_name") + "\" title=\"Specify topic name\" readonly/></td>");
									}
			
									if (request.getParameter("topic-table-name-" + idx) != null) {
										String val = request.getParameter("topic-table-name-" + idx);
										out.println("<td><input type=\"text\" id=\"topic-table-name-" + idx + "\" name=\"topic-table-name-" + idx + "\" value=\"" + val + "\" title=\"Specify table name to create for this topic\" readonly/></td>");
									} else {
										out.println("<td><input type=\"text\" id=\"topic-table-name-" + idx + "\" name=\"topic-table-name-" + idx + "\" value=\"" + rs.getString("topic_table_name") + "\" title=\"Specify table name to create for this topic\" readonly/></td>");
									}
			
									if (request.getParameter("topic-field-count-" + idx) != null) {
										String val = request.getParameter("topic-field-count-" + idx);
										out.println("<td><input type=\"text\" id=\"topic-field-count-" + idx + "\" name=\"topic-field-count-" + idx + "\" value=\"" + val + "\" title=\"Specify field count in each message published in this topic\" readonly/></td>");
			
									} else {
										out.println("<td><input type=\"text\" id=\"topic-field-count-" + idx + "\" name=\"topic-field-count-" + idx + "\" value=\"" + rs.getString("topic_field_count") + "\" title=\"Specify field count in each message published in this topic\" readonly/></td>");
									}
			
									if (request.getParameter("topic-create-table-sql-" + idx) != null) {
										String val = request.getParameter("topic-create-table-sql-" + idx);
										out.println("<td><textarea id=\"topic-create-table-sql-" + idx + "\" name=\"topic-create-table-sql-" + idx + "\" rows=\"4\" cols=\"50\"" + "\" value=\"" + val + "\" title=\"Specify create table sql using SQLite syntax\" readonly/>" + val + "</textarea></td>");
									} else {
										out.println("<td><textarea id=\"topic-create-table-sql-" + idx + "\" name=\"topic-create-table-sql-" + idx + "\" rows=\"4\" cols=\"50\"" + "\" value=\"" + rs.getString("topic_create_table_sql") + "\" title=\"Specify create table sql using SQLite syntax\" readonly/>" + rs.getString("topic_create_table_sql") + "</textarea></td>");							
									}
			
									out.println("</tr>");
									++idx;
								}
							}
						}
					}
					
					for (; idx < numTopics; ++idx) {
						out.println("<tr>");
						if (request.getParameter("enable-" + idx) != null) {
							out.println("<td><input type=\"checkbox\" id=\"enable-" + idx + "\" name=\"enable-" + idx + "\" value=\"" + "1" + "\" checked/></td>");
						} else {
							out.println("<td><input type=\"checkbox\" id=\"enable-" + idx + "\" name=\"enable-" + idx + "\" value=\"" + "0" + "\"/></td>");
						}
						if (request.getParameter("topic-name-" + idx) != null) {
							String val = request.getParameter("topic-name-" + idx);
							out.println("<td><input type=\"text\" id=\"topic-name-" + idx + "\" name=\"topic-name-" + idx + "\" value=\"" + val + "\" title=\"Specify topic name\"/></td>");
						} else {
							out.println("<td><input type=\"text\" id=\"topic-name-" + idx + "\" name=\"topic-name-" + idx + "\" value=\"" + "\" title=\"Specify topic name\"/></td>");
						}
						if (request.getParameter("topic-table-name-" + idx) != null) {
							String val = request.getParameter("topic-table-name-" + idx);
							out.println("<td><input type=\"text\" id=\"topic-table-name-" + idx + "\" name=\"topic-table-name-" + idx + "\" value=\"" + val + "\" title=\"Specify table name to create for this topic\"/></td>");
						} else {
							out.println("<td><input type=\"text\" id=\"topic-table-name-" + idx + "\" name=\"topic-table-name-" + idx + "\" value=\"" + "\" title=\"Specify table name to create for this topic\"/></td>");
						}
						if (request.getParameter("topic-field-count-" + idx) != null) {
							String val = request.getParameter("topic-field-count-" + idx);
							out.println("<td><input type=\"text\" id=\"topic-field-count-" + idx + "\" name=\"topic-field-count-" + idx + "\" value=\"" + val + "\" title=\"Specify field count in each message published in this topic\"/></td>");
						} else {
							out.println("<td><input type=\"text\" id=\"topic-field-count-" + idx + "\" name=\"topic-field-count-" + idx + "\" value=\"" + "\" title=\"Specify field count in each message published in this topic\"/></td>");
						}
						
						if (request.getParameter("topic-create-table-sql-" + idx) != null) {
							String val = request.getParameter("topic-create-table-sql-" + idx);
							out.println("<td><textarea id=\"topic-create-table-sql-" + idx + "\" name=\"topic-create-table-sql-" + idx + "\" rows=\"4\" cols=\"50\"" + "\" value=\"" + val + "\" title=\"Specify create table sql using SQLite syntax\"/>" + val + "</textarea></td>");
						} else {
							out.println("<td><textarea id=\"topic-create-table-sql-" + idx + "\" name=\"topic-create-table-sql-" + idx + "\" rows=\"4\" cols=\"50\"" + "\" value=\"" + "\" title=\"Specify create table sql using SQLite syntax\"/>" + "</textarea></td>");
						}
						out.println("</tr>");
					}

					out.println("<input type=\"hidden\" id=\"num-tables\" name=\"num-tables\" value=\"" + idx + "\"/>");
					%>
				</tbody>
			</table>
			<center>
				<button type="submit" name="start">Start</button>
			</center>			
		</form>
	</div>
	
<script type="text/javascript">
	// Get references to the checkboxes
	const enableAllCheckbox = document.getElementById("enable-all");
	const individualCheckboxes = document.querySelectorAll('input[type="checkbox"][name^="enable-"]');
	
	// Add an event listener to the "enable-all" checkbox
	enableAllCheckbox.addEventListener("change", function() {
	  const isChecked = enableAllCheckbox.checked;
	  // Set the state of individual checkboxes to match the "enable-all" checkbox
	  individualCheckboxes.forEach(function(checkbox) {
	    checkbox.checked = isChecked;
	  });
	});
</script>
	
</body>
</html>
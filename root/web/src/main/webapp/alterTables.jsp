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

<title>Alter Topic Table Schemas</title>
</head>
<body>
	<%@include file="html/menu.html"%>	

	<div class="main">
		<h2>Alter Topic Schemas</h2>
		Note: Please stop the job and scheduler if running before submitting this job.<br>
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

			String errorMsg = request.getParameter("errorMsg");
			if (errorMsg != null) {
				out.println("<h4 style=\"color: red;\">" + errorMsg + "</h4>");
			}

			String alterTableSQLs = "";
			if (request.getParameter("alter-table-sqls") != null) {
				alterTableSQLs = request.getParameter("alter-table-sqls");				
			}
			
			String alterTableSqlPlaceholderText = "ALTER TABLE tab1 ADD COLUMN col5 INTEGER;" + "\n" + "ALTER TABLE tab2 DROP COLUMN col5;";
		%>

		<form action="${pageContext.request.contextPath}/altertables"	method="post">
			<table>
				<tbody>
				<tr>
					<td>
						Alter Table SQLs
					</td>
					<td>
						<textarea id="alter-table-sqls" name="alter-table-sqls" rows="30" cols="100" value="<%=alterTableSQLs%>"  title="Specify alter table SQL statements for topic tables." placeholder="<%=alterTableSqlPlaceholderText%>"><%=alterTableSQLs%></textarea>
					</td>						
				</tr>
				</tbody>
			</table>
			<center>
				<button type="submit" name="submit">Submit</button>
			</center>			
		</form>
	</div>
</body>
</html>
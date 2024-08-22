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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

	private static Path dbDir;
	private static Path qReaderConfigFilePath;
	public static SyncLiteAppLock appLock = new SyncLiteAppLock();
	public static CMDType CMD;

	public static void main(String[] args) {
		try {
			if (args.length != 5) {
				usage();
			} else {
				String cmd = args[0].trim().toLowerCase();
				if (! (cmd.equals("read") || (cmd.equals("schema-change")))) {
					usage();
				} else {
					String cmdTxt = args[0].trim().toLowerCase();
					
					if (cmdTxt.equals("read")) {
						CMD = CMDType.READ;
					} else if (cmdTxt.equals("schema-change")) {
						CMD = CMDType.SCHEMA_CHANGE;
					} else {
						usage();
					}
				}

				if (!args[1].trim().equals("--db-dir")) {
					usage();
				} else {
					dbDir = Path.of(args[2]);
					if (!Files.exists(dbDir)) {
						error("Invalid db-dir specified");
					}
				}

				if (!args[3].trim().equals("--config")) {
					usage();
				} else {
					qReaderConfigFilePath = Path.of(args[4]);
					if (!Files.exists(qReaderConfigFilePath)) {
						error("Invalid qreader configuration file specified");
					}
				}				

				tryLockDBDir();
				ConfLoader.getInstance().loadQReaderConfigProperties(qReaderConfigFilePath);
				
				if (CMD == CMDType.READ) {
					QReaderDriver.getInstance().run();
				} else if (CMD == CMDType.SCHEMA_CHANGE) {
					SchemaChangeDriver.getInstance().run();
				}
			}
		} catch (Exception e) {		
			try {				 
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String stackTrace = sw.toString();
				Path exceptionFilePath; 
				if (dbDir == null) { 
					exceptionFilePath = Path.of("synclite_qreader_exception.trace");
				} else {
					exceptionFilePath = dbDir.resolve("synclite_qreader_exception.trace");
				}

				String finalStr = e.getMessage() + "\n" + stackTrace;
				Files.writeString(exceptionFilePath, finalStr);
				System.out.println("ERROR : " + finalStr);
				System.err.println("ERROR : " + finalStr);	
			} catch (Exception ex) {
				System.out.println("ERROR : " + ex);
				System.err.println("ERROR : " + ex);	
			}
			System.exit(1);
		}

	}

	private static final void tryLockDBDir() throws SyncLiteException {
		appLock.tryLock(dbDir);
	}

	private static final void error(String message) throws Exception {
		System.out.println("ERROR : " + message);
		throw new Exception("ERROR : " + message);
	}

	private static final void usage() {
		System.out.println("ERROR :");
		System.out.println("Usage : ");
		System.out.println("SyncLiteQReader read --db-dir <path/to/db-dir> --config <path/to/qreader_config>");
		System.out.println("SyncLiteQReader schema-change --db-dir <path/to/db-dir> --config <path/to/qreader_config>");
		System.exit(1);
	}
}

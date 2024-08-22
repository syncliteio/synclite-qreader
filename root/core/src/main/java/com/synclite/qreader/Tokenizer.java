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

public abstract class Tokenizer {
	public abstract String[] tokenize(byte[] msg);
	
	private static final class InstanceHolder {
		private static Tokenizer INSTANCE = instantiate();

		private static Tokenizer instantiate() {
			switch (ConfLoader.getInstance().getMessageFormat()) {
			case CSV:
				return new CSVTokenizer();
			default:
				return new CSVTokenizer();
			}
		}
	}
	
	public static Tokenizer getInstance() throws SyncLiteException {
		return InstanceHolder.INSTANCE;
	}
}

package com.winterwell.web.app;

public enum KServerType {
	LOCAL("local"), TEST("test"), STAGE("stage"), PRODUCTION("");

	public final String prefix;

	KServerType(String prefix) {
		this.prefix = prefix;
	}
		
}

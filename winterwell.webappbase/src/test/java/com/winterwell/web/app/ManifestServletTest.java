package com.winterwell.web.app;

import org.junit.Test;

import com.winterwell.datalog.DataLogEvent;

public class ManifestServletTest {

	@Test
	public void testSetVersionGitCommitTime() {
		DataLogEvent d = new DataLogEvent("test", 1);
		ManifestServlet.setVersionGitCommitTime(d);
		System.out.println(d);
		System.out.println(d.getProp("ver"));
	}

}

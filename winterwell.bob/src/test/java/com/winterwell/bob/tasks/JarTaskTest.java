package com.winterwell.bob.tasks;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;


public class JarTaskTest {

	/**
	 * These jar files are giving "invalid archive" messages when examined through the Windows file explorer
	 * TODO check in Linux
	 */
	@Test @Ignore // fails?!
	public void testWindowsWierdness() {
		File dir = new File("bin");
		File jarFile = new File("testing/test.jar");
		JarTask jt = new JarTask(jarFile, dir);
		jt.run();
	}
}

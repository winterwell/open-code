package com.winterwell.bob.tasks;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.winterwell.bob.wwjobs.BuildUtils;

public class JavaDocTaskTest {

	@Test
	public void testJavaDocUtils() throws Exception {
		File outDir = new File("test-out/javadoc/utils");
		outDir.mkdirs();
		File srcDir = new File(new WinterwellProjectFinder().apply("winterwell.utils"), "src");
		assert srcDir.isDirectory() : srcDir.getAbsolutePath();
		File jarDir = new BuildUtils().getBuildJarsDir();
		List<File> cp = Arrays.asList(jarDir.listFiles());
		JavaDocTask jdt = new JavaDocTask("com.winterwell.utils", srcDir, outDir, cp);
		jdt.doTask();
	}

}

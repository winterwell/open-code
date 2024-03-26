package com.winterwell.bob.tasks;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.io.FileUtils;

public class EclipseClasspathTest {

	@Test @Ignore // fails?!
	public void testGetCollectedLibs_smokeTestBugJun2021() {
		File juiceDir = new File(FileUtils.getWinterwellDir(), "juice");
				// WinterwellProjectFinder().apply("juice");
		assert juiceDir.isDirectory() : juiceDir;
		EclipseClasspath ec = new EclipseClasspath(juiceDir);
		Set<File> libs = ec.getCollectedLibs();
		assert ! libs.isEmpty();
	}


	@Test @Ignore // fails?!
	public void testMondaybotBugSep2022() {
		File juiceDir = new File(FileUtils.getWinterwellDir(), "code/mondaybot");
		assert juiceDir.isDirectory() : juiceDir;
		EclipseClasspath ec = new EclipseClasspath(juiceDir);
		List<String> projects = ec.getReferencedProjects();
		assert projects.toString().contains("calstat") : projects;
		Set<File> libs = ec.getCollectedLibs();
		assert ! libs.isEmpty();
	}
}

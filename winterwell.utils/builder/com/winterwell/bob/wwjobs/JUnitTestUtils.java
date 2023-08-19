package com.winterwell.bob.wwjobs;

import java.io.File;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.JUnitTask;
import com.winterwell.bob.tasks.WinterwellProjectFinder;
import com.winterwell.utils.gui.GuiUtils;

public class JUnitTestUtils extends BuildTask {

	@Override
	protected void doTask() throws Exception {
		BuildUtils bu = new BuildUtils();
		String projectName = "winterwell.utils";
		File projDir = new WinterwellProjectFinder().apply(projectName);
		File outputFile = new File(projDir, "test-results/"+projectName+".html");
		GuiUtils.setInteractive(false);
		JUnitTask junit = new JUnitTask(
				new File(projDir, "src/java"),  // bu.getJavaSrcDir(), 
				new File(projDir, "bin.test"),	// bu.getTestBinDir(),
				outputFile);		
		junit.run();		
		int good = junit.getSuccessCount();
		int bad = junit.getFailureCount();
	}

}

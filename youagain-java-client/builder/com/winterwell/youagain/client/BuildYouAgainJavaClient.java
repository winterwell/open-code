
package com.winterwell.youagain.client;

import java.io.File;
import java.util.List;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.MavenDependencyTask;
import com.winterwell.bob.wwjobs.BuildHacks;
import com.winterwell.bob.wwjobs.BuildWinterwellProject;
import com.winterwell.web.app.KServerType;


public class BuildYouAgainJavaClient extends BuildWinterwellProject {

	public BuildYouAgainJavaClient() {
		super("youagain-java-client");
		setIncSrc(true);
		setVersion("1.0.7"); // 14 June 2022
	}

	@Override
	public List<BuildTask> getDependencies() {
		List<BuildTask> deps = super.getDependencies();

		MavenDependencyTask mdt = new MavenDependencyTask();		
		mdt.addDependency("com.auth0", "java-jwt", "3.15.0");
		mdt.setOutputDirectory(new File(projectDir, "dependencies"));
		mdt.setIncSrc(BuildHacks.getServerType()==KServerType.LOCAL);
//		mdt.setForceUpdate(true);

		deps.add(mdt);
		return deps;
	}
	
	@Override
	public void doTask() throws Exception {		
		super.doTask();
	}

}

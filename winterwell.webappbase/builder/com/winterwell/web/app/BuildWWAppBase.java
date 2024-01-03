package com.winterwell.web.app;

import java.util.List;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.MavenDependencyTask;
import com.winterwell.bob.wwjobs.BuildWinterwellProject;

public class BuildWWAppBase extends BuildWinterwellProject {

	
	public BuildWWAppBase() {
		super("winterwell.webappbase");
		setVersion("1.2.1"); // Jan 2023
		setIncSrc(true);
	}
	
	@Override
	public List<BuildTask> getDependencies() {
		List<BuildTask> deps = super.getDependencies();
		
		MavenDependencyTask mdt = new MavenDependencyTask();
		deps.add(mdt);
		// from winterwell.web
		mdt.addDependency("org.eclipse.jetty:jetty-servlet:10.0.7");
		mdt.addDependency("com.sun.mail", "jakarta.mail", "2.0.1"); //"1.5.0-b01");
		return deps;
	}
	

}

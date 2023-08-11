package com.winterwell.bob.wwjobs;

import java.util.ArrayList;
import java.util.List;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.MavenDependencyTask;
import com.winterwell.web.app.KServerType;

public class BuildWeb extends BuildWinterwellProject {

	/**
	 * Build winterwell.web
	 */
	public BuildWeb() {
		super("winterwell.web");
		setVersion("1.1.4"); // 31 May 2022
	}	

	@Override
	public List<BuildTask> getDependencies() {
		ArrayList deps = new ArrayList(super.getDependencies());

		// utils
		deps.add(new BuildUtils());
		
		// maven
		MavenDependencyTask mdt = new MavenDependencyTask();
		// see https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-server
		String jettyVersion = 
				"10.0.15"; // websockets
//				"10.0.7"; // hopefully fix Ambiguous segment error for slugs with an encoded "/"
		// NB: v11 is the same as v10 but switches s/javax/jakarta/
//				"9.4.24.v20191120"; 
		mdt.addDependency("org.eclipse.jetty", "jetty-server", jettyVersion);
		mdt.addDependency("org.eclipse.jetty","jetty-util",jettyVersion);
		mdt.addDependency("org.eclipse.jetty","jetty-util-ajax",jettyVersion);
		mdt.addDependency("org.eclipse.jetty", "jetty-servlet",jettyVersion);
		
		// https://mvnrepository.com/artifact/com.sun.mail/jakarta.mail
		mdt.addDependency("com.sun.mail", "jakarta.mail", "2.0.1"); 
//		mdt.addDependency("com.sun.mail", "javax.mail", "1.6.2"); //"1.5.0-b01");
		mdt.addDependency("com.sun.mail", "gimap", "2.0.1"); // used??
//		mdt.addDependency("javax.mail", "javax.mail-api", "1.6.1"); //"1.5.0-b01");
//		mdt.addDependency("javax.mail", "imap", "1.4"); //"1.5.0-b01");
		
		mdt.addDependency("eu.medsea.mimeutil", "mime-util", "1.3"); // latest is "2.1.3" but that seems borked
		mdt.addDependency("org.apache.httpcomponents", "httpclient", "4.5.10");
		mdt.addDependency("org.ccil.cowan.tagsoup", "tagsoup", "1.2.1");
		// Apache
		mdt.addDependency("commons-fileupload","commons-fileupload","1.4");
		mdt.addDependency("commons-io", "commons-io", "2.6");
		// https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
		mdt.addDependency("org.apache.commons","commons-lang3","3.9");

		// DNS Java -- NB: Latest is v3 Carson tried it in 2019, and it had oddly poor outputs! (worse than v2) 
		// Surely it must be better if we have the time to work it out? OTOH v2 is fine and we don't care.
		mdt.addDependency("dnsjava", "dnsjava", "2.1.9");
		
		mdt.setIncSrc(true); //always true to avoid classpath accidents BuildHacks.getServerType()==KServerType.LOCAL); // we like source code
//		mdt.setForceUpdate(true);
		mdt.setProjectDir(projectDir);
		if (outDir!=null) {
			mdt.setOutputDirectory(outDir);
		}
		deps.add(mdt);
		
		return deps;
	}
	
}

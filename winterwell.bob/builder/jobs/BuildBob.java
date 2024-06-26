package jobs;

import java.io.File;
import java.util.List;

import com.winterwell.bob.Bob;
import com.winterwell.bob.BobConfig;
import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.MavenDependencyTask;
import com.winterwell.bob.tasks.WinterwellProjectFinder;
import com.winterwell.bob.wwjobs.BuildHacks;
import com.winterwell.bob.wwjobs.BuildWinterwellProject;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.web.app.KServerType;

/**
 * Naturally Bob is built by Bob.
 * 
 * You can get the latest version of Bob from:
 * https://www.winterwell.com/software/downloads/bob-all.jar
 * 
 * To update Bob and release a new version:
 * 
 * Bump the version number in BobConfig. 

Then run BuildBob
﻿Then on servers etc run `bob -update` ﻿﻿(which might be part of the build script)
 * 
 * @see BobConfig
 * @author daniel
 *
 */
public class BuildBob extends BuildWinterwellProject {

	public BuildBob() {
		super(new WinterwellProjectFinder().apply("winterwell.bob"), "bob");
		incSrc = true;
		setMainClass(Bob.class.getCanonicalName());
		
		// For releasing Bob
		if (BuildHacks.getServerType()==KServerType.LOCAL) {
			setMakeFatJar(true);
		}
		
		// Manually set the version
		String v = BobConfig.VERSION_NUMBER;
		setVersion(v);
		
		setScpToWW(false); //isTopLocalBuild);
	}

	@Override
	public void doTask() throws Exception { 
		super.doTask();
		
		// needed??
		// also make "winterwell.bob.jar" for other builds to find (e.g. BuildJerbil)
		File bobjar = getJar();
		FileUtils.copy(bobjar, new File(projectDir, "winterwell.bob.jar"));
		
		// bob-all.jar is what you want to run Bob
		
		// Update the readme version
		if (isTopLocalBuild) {
			String readme = FileUtils.read(new File(projectDir, "README.md"));
			String readme2 = readme.replaceFirst("Latest version:\\s*[0-9.]+", "Latest version: "+BobConfig.VERSION_NUMBER);
			FileUtils.write(new File(projectDir, "README.md"), readme2);
		}
	}

	@Override
	public List<BuildTask> getDependencies() {
		List<BuildTask> uw = super.getDependencies();
		
		MavenDependencyTask mdt = new MavenDependencyTask();
		uw.add(mdt);
		
		
		mdt.addDependency("good-loop.com", "winterwell.utils", "0.0.1-SNAPSHOT");
		
//		mdt.addDependency("org.slf4j", "slf4j-api", "1.7.30");

		// https://mvnrepository.com/artifact/commons-net/commons-net
		mdt.addDependency("commons-net","commons-net","3.6");
		// https://mvnrepository.com/artifact/com.jcraft/jsch
		mdt.addDependency("com.jcraft","jsch","0.1.55");
				
		// from winterwell.utils
		mdt.addDependency("com.thoughtworks.xstream","xstream","1.4.19");
		// from winterwell.web
		// maven
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
		
		mdt.addDependency("com.sun.mail", "jakarta.mail", "2.0.1"); //"1.5.0-b01");
		mdt.addDependency("com.sun.mail", "gimap", "2.0.1"); // used??
//		mdt.addDependency("jakarta.mail", "jakarta.mail-api", "1.6.1"); //"1.5.0-b01");
//		mdt.addDependency("jakarta.mail", "imap", "1.4"); //"1.5.0-b01");
		
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
		
		mdt.setIncSrc(true); // we like source code
//		mdt.setForceUpdate(true);
		mdt.setProjectDir(projectDir);
		if (outDir!=null) {
			mdt.setOutputDirectory(outDir);
		}

		return uw;
	}

}

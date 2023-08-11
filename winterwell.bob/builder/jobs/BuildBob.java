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
		
		setScpToWW(isTopLocalBuild);
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
//		mdt.addDependency("org.slf4j", "slf4j-api", "1.7.30");

		// https://mvnrepository.com/artifact/commons-net/commons-net
		mdt.addDependency("commons-net","commons-net","3.6");
		// https://mvnrepository.com/artifact/com.jcraft/jsch
		mdt.addDependency("com.jcraft","jsch","0.1.55");
		
		uw.add(mdt);
		return uw;
	}

}

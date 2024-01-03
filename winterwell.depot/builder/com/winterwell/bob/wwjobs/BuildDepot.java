package com.winterwell.bob.wwjobs;

import java.util.List;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.MavenDependencyTask;

public class BuildDepot extends BuildWinterwellProject {

	public BuildDepot() {
		super("winterwell.depot");
		setVersion("1.1.0"); // Jan 2022 - move default remote to datastore.good-loop.com
	}
	
	@Override
	public List<BuildTask> getDependencies() {
		List<BuildTask> deps = super.getDependencies();

		MavenDependencyTask mdt = new MavenDependencyTask();
		deps.add(mdt);
		// from winterwell.utils
		mdt.addDependency("com.thoughtworks.xstream","xstream","1.4.19");

		
		return deps;
	}

}

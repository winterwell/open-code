
package com.goodloop.locksmith;

import java.util.List;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.tasks.MavenDependencyTask;
import com.winterwell.bob.wwjobs.BuildWinterwellProject;

public class BuildLockSmith extends BuildWinterwellProject {

	public BuildLockSmith() {
		super("goodloop.locksmith");
		setVersion("0.1.0"); // Sep 2023
	}	

	@Override
	public List<BuildTask> getDependencies() {
		List<BuildTask> deps = super.getDependencies();
		
//		MavenDependencyTask mdt = new MavenDependencyTask();
//		deps.add(mdt);
		
		return deps;
	}	

}

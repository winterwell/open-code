package com.winterwell.web.app;

import com.winterwell.bob.wwjobs.BuildWinterwellProject;

public class BuildWWAppBase extends BuildWinterwellProject {

	
	public BuildWWAppBase() {
		super("winterwell.webappbase");
		setVersion("1.2.1"); // Jan 2023
		setIncSrc(true);
	}
	

}

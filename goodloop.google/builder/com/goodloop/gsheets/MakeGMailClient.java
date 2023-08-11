//package com.goodloop.gsheets;
//import java.util.List;
//
//import com.winterwell.bob.BuildTask;
//import com.winterwell.bob.tasks.MavenDependencyTask;
//import com.winterwell.bob.wwjobs.BuildHacks;
//import com.winterwell.bob.wwjobs.BuildWinterwellProject;
//import com.winterwell.web.app.KServerType;
///**
// * See {@link GSheetsClient}
// * @author daniel
// *
// */
//public class MakeGMailClient extends BuildWinterwellProject {
//
//	public MakeGMailClient() {
//		super("goodloop.google");
//		setIncSrc(true);
////		setVersion("0.2.0"); // Dec 2022
//	}
//	
//	@Override
//	public List<BuildTask> getDependencies() {
//		List<BuildTask> deps = super.getDependencies();
//
//		MavenDependencyTask mdt = new MavenDependencyTask();
//		// https://mvnrepository.com/artifact/com.google.api-client/google-api-client
//		mdt.addDependency("com.google.api-client:google-api-client:1.35.2"); // Google Sheets doesn't like 2.1.1 (Dec 2022)");
//		
//		mdt.addDependency("com.google.oauth-client:google-oauth-client-jetty:1.34.1");
//		mdt.addDependency("com.google.apis:google-api-services-sheets:v4-rev612-1.25.0");
//		mdt.addDependency("com.google.http-client:google-http-client-jackson2:1.39.2");
//		// calendar support??
//		mdt.addDependency("com.google.apis:google-api-services-calendar:v3-rev20210429-1.31.0");
//		mdt.setIncSrc(BuildHacks.getServerType()==KServerType.LOCAL);
////		mdt.setKeepPom(true);
//		deps.add(mdt);
//				
//		return deps;
//	}
//
//}

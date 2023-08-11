
package com.winterwell.datalog;

import com.winterwell.bob.tasks.WinterwellProjectFinder;
import com.winterwell.bob.wwjobs.BuildWinterwellProject;

/**
 * Hm: if this is called BuildX, then Bob will not be able to choose between this and BuildDataLog.
 * So renamed to MakeX
 * @author daniel
 *
 */
public class MakeReindexer extends BuildWinterwellProject {

	public static void main(String[] args) throws Exception {
		MakeReindexer b = new MakeReindexer();
		b.doTask();
	}
	
	public MakeReindexer() {
		super(new WinterwellProjectFinder().apply("winterwell.datalog"), "datalog.reindex");
		setVersion("1.0.0"); // Oct 2022
		setScpToWW(false);
		setMainClass("com.winterwell.datalog.server.DataLogReindexMain");
	}	

}

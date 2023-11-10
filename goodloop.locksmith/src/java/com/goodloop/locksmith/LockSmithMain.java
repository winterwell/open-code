package com.goodloop.locksmith;

import com.goodloop.adserver.GLBaseConfig;
import com.winterwell.utils.Dep;
import com.winterwell.web.app.AMain;
import com.winterwell.web.app.JettyLauncher;
import com.winterwell.web.app.MasterServlet;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * Runs this for a standalone DataLog micro-service server.
 * 
 * @author daniel
 * 
 */
public class LockSmithMain extends AMain<LockSmithConfig> {

	public LockSmithMain() {
		super("locksmith", LockSmithConfig.class);
		localDatalog = false;

	}
	
	public static void main(String[] args) {	
		LockSmithMain dls = new LockSmithMain();
		dls.doMain(args);
		
		// HACK 1: Magic string "good-loop" - rather than adding adserver
		// to classpath just for GLBaseConfig.GOOD_LOOP_APPNAME
		// HACK 2: product doesn't do anything yet, so this doesn't
		// necessarily tie locksmith to portal
		YouAgainClient yac = new YouAgainClient("good-loop", "portal.good-loop.com");
		Dep.set(YouAgainClient.class, yac);
	}
	
	@Override
	protected void addJettyServlets(JettyLauncher jl) {
		super.addJettyServlets(jl);
		MasterServlet ms = jl.addMasterServlet();	
		ms.addServlet("/lock", LockServlet.class);
	}

	@Override
	protected void init2(LockSmithConfig config) {
		super.init2(config);
	}

}

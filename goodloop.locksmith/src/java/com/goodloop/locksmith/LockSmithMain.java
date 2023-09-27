package com.goodloop.locksmith;

import java.util.Arrays;
import java.util.Timer;

import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.IDataLogAdmin;
import com.winterwell.utils.Dep;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.log.LogFile;
import com.winterwell.utils.time.TUnit;
import com.winterwell.web.app.AMain;
import com.winterwell.web.app.AppUtils;
import com.winterwell.web.app.JettyLauncher;
import com.winterwell.web.app.KServerType;
import com.winterwell.web.app.MasterServlet;

/**
 * Runs this for a standalone DataLog micro-service server.
 * 
 * @author daniel
 * 
 */
public class LockSmithMain extends AMain<LockSmithConfig> {

	public LockSmithMain() {
		super("locksmith", LockSmithConfig.class);
	}
	
	public static void main(String[] args) {	
		LockSmithMain dls = new LockSmithMain();
		dls.doMain(args);
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

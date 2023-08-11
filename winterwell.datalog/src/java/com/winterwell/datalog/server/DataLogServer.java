package com.winterwell.datalog.server;

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

/**
 * Runs this for a standalone DataLog micro-service server.
 * 
 * @author daniel
 * 
 */
public class DataLogServer extends AMain<DataLogConfig> {

	DataLogReindexMain dummy1; // ensure this gets compiled
	CompressDataLogIndexMain dummy2; // ensure this gets compiled
	
	/**
	 * static ref to config
	 * @deprecated why not use config? wiring with LgServlet is a bit clumsy
	 */
	static DataLogConfig settings;
	
	Timer geoLiteUpdateTimer;

	public DataLogServer() {
		super("datalog", DataLogConfig.class);
	}
	
	public static void main(String[] args) {	
		DataLogServer dls = new DataLogServer();
		dls.doMain(args);
	}
	
	@Override
	protected void addJettyServlets(JettyLauncher jl) {
		jl.addServlet("/*", new MasterHttpServlet());
	}

	@Override
	protected void init2(DataLogConfig config) {
		this.settings = config;
		// set skipped IPs if unset
		if (config.ourSkippedIPs == null) {
			// test / local / prod??
			if (AppUtils.getServerType() == KServerType.LOCAL || AppUtils.getServerType() == KServerType.TEST) {
				Log.d("init", "No ourSkippedIPS 'cos "+AppUtils.getServerType());
			} else {
				// HACK - don't log GL office activty
				config.ourSkippedIPs = Arrays.asList(
						"62.30.12.102", // ??which office
						"62.6.190.196", // ??which office 
						"82.37.169.72" // ??which office
						);
				Log.d("init", "Set ourSkippedIPS from null to "+config.ourSkippedIPs+" (GL office)");
			}
		}
		
		logFile = new LogFile(config.logFile)
				// keep 6 weeks of log files so we can do 1 month reports
				.setLogRotation(TUnit.DAY.dt, 6*7);
		// set the config
		DataLog.init(config);
		// usual setup
		super.init2(config);
		init3_youAgain();
		
		// Prepare GeoLiteLocator to attach IP-inferred country codes to events
		GeoLiteLocator gll = new GeoLiteLocator();
		Dep.set(GeoLiteLocator.class, gll);
		
		// Check for updates every 24 hours & store timer in case the JVM tries to garbage collect it
		geoLiteUpdateTimer = new Timer(true);
		geoLiteUpdateTimer.scheduleAtFixedRate(gll.getUpdateTask(), 0, TUnit.DAY.millisecs);
		
		// register the tracking event
		IDataLogAdmin admin = DataLog.getAdmin();
		admin.registerDataspace(DataLog.getDataspace());
	}
	


}

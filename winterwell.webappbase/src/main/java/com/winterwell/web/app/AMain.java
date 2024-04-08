package com.winterwell.web.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.winterwell.bob.tasks.GitTask;
import com.winterwell.bob.wwjobs.BuildHacks;
import com.winterwell.datalog.DataLog;
import com.winterwell.es.IESRouter;
import com.winterwell.es.StdESRouter;
import com.winterwell.es.XIdTypeAdapter;
import com.winterwell.es.client.ESConfig;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.gson.Gson;
import com.winterwell.gson.GsonBuilder;
import com.winterwell.gson.KLoopPolicy;
import com.winterwell.gson.StandardAdapters;
import com.winterwell.utils.AString;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Mutable;
import com.winterwell.utils.Mutable.Ref;
import com.winterwell.utils.Printer;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.ConfigBuilder;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.ILogListener;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.log.LogConfig;
import com.winterwell.utils.log.LogFile;
import com.winterwell.utils.log.SystemOutLogListener;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.data.XId;
import com.winterwell.web.email.EmailConfig;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * Does three things:
 * 1. Initialise config from properties
 * 2. Start a web-server
 * 3. Run a main loop
 * 
 * Common main/init code. To use:
 * 
 * 1. Create subclass. You should probably override
 * 	{@link #addJettyServlets(JettyLauncher)}
 * and maybe
 * {@link #doMain2()}
 * 
 * 2. Add a main method to the subclass:
 * <pre><code>
 * public static void main(String[] args) {
		MyMain amain = new MyMain();
		amain.doMain(args);
	}
 *  </code></pre>
 * 
 * That's it for basic use :)
 * 
 * @author daniel
 *
 * @param <ConfigType>
 */
public abstract class AMain<ConfigType extends ISiteConfig> {

	public static final String copyright = "(C) Good-Loop Ltd";
	
	protected JettyLauncher jl;
	
	/**
	 * aka app local name e.g. "myapp" (and NOT myapp.mydomain.com)
	 */
	@Deprecated // access via the non-static getAppName() for preference
	public static String appName;
	
	/**
	 * @return e.g. "myapp" Note: Use "myapp.mydomain.com" with YouAgain
	 * @see #getAppNameFull()
	 */
	public String getAppNameLocal() {
		return appName;
	}
	
	/**
	 * Set this to use e.g. SLF4J
	 */
	protected Class loggerClass;
	public static LogFile logFile;
	public static LogFile auditlogFile;

	protected static boolean initFlag;

	protected ConfigType config;

	protected Class<ConfigType> configType;

	protected volatile boolean pleaseStop;

	private Thread mainLoopThread;
	
	private volatile boolean readyFlag;

	/**
	 * Unconsumed main args, after initConfig()
	 */
	protected List<String> configRemainderArgs;

	protected boolean noJetty;

	/**
	 * If true (the default) then a local DataLog is run. If there is no database settings -- it may try a few times then fail.
	 * If false, then local DataLog calls will silently do nothing.
	 * 
	 * This does NOT affect calls to DataLogRemoteStorage
	 */
	protected boolean localDatalog = true;
	

	/**
	 * @deprecated avoid static if possible
	 */
	public static AMain main;

	/**
	 * a convenient place for a global debug flag
	 */
	public static boolean debug;
	
	public static void setDebug(boolean debug) {
		AMain.debug = debug;
	}
	

	/**
	 * @deprecated This will guess the appName from the folder -- better to sepcify it. 
	 */
	public AMain() {
		this(FileUtils.getWorkingDirectory().getName().toLowerCase(), null);
	}
	
	/**
	 * @param projectName aka app local name e.g. "myapp" (and NOT myapp.mydomain.com)
	 */
	public AMain(String projectName, Class<ConfigType> configType) {
		this.appName = projectName;
		this.configType = configType;
	}

	public ConfigType getConfig() {
		return config;
	}
	
	/**
	 * Call this to run your app!
	 * 
	 * NB: this should return after starting up Jetty. i.e. it does not sit in a forever loop.
	 * 
	 * Normally leave this alone and override {@link #doMain2()} and {@link #doMainLoop()}
	 * 
	 * @param args
	 */
	public void doMain(String[] args) {
		String info = "";
		try {
			Map<String, Object> gitInfo = GitTask.getLastCommitInfo(new File(""));
			info += gitInfo;
		} catch(Throwable ex) {
			// ignore
		}
		Log.d(appName, "Starting! "+info);
		try {
			Thread.currentThread().setName(getClass().getSimpleName()+".doMain");
			// logfile before log config??! Is that right?
			if (loggerClass==null || Log.class.equals(loggerClass)) {
				LogConfig logConfig = ConfigFactory.get().getConfig(LogConfig.class);
				Log.setConfig(logConfig);
				// Try to use the "logs" subdirectory - but use the app root if that's impossible.
				File logDir = new File("logs");
				boolean useSubDir = true;
				if (!logDir.exists()) {
					// Try to create it - use dir if successful
					useSubDir = logDir.mkdir();
				} else if (!logDir.isDirectory()) {
					// Exists but isn't a directory - don't use dir
					useSubDir = false;
				}
				File logLocation = new File((useSubDir ? "logs/" : "") + getAppNameLocal() + ".log"); 
				// NB: this log setup will call ConfigFactory early (before the full init)
				logFile = new LogFile(logLocation).setLogRotation(TUnit.DAY.dt, 14);
				
				// also add a never-rotates! audit log for important audit trail info only (ie stuff tagged "audit"		
				File auditlogLocation = new File((useSubDir ? "logs/" : "") + getAppNameLocal() + ".audit");
				auditlogFile = new LogFile(auditlogLocation).setFilter(r -> "audit".equals(r.tag));
			} else if (org.slf4j.Logger.class.equals(loggerClass)) {				
				Log.addListener(new SLF4JLogListener());
			}
			
			// don't log to sysout on prod (blockage seen there with contended threads dec 2021)
			if (BuildHacks.getServerType() != KServerType.LOCAL) {
				Log.d(appName, "Removing SystemOutLogListener");
				List<ILogListener> ls = Log.getListeners();
				for(ILogListener ll : ls) {
					if (ll instanceof SystemOutLogListener) {
						Log.removeListener(ll);
					}
				}
			}
			
			try {
				assert "foo".contains("bar");
				Log.e("run", "Running Java WITHOUT assertions - please use the -ea flag!");
			} catch(AssertionError e) {
				// ok
			}
			Log.i(getAppNameLocal(), "doMain "+Printer.toString(args)+" ...Let's go :)");

			// Init!
			init(args);
			
			if (noJetty) {
				// allow jetty to be disabled
			} else {
				launchJetty();
			}
			
			// do Main once
			doMain2();
			
			// loop? (does nothing but stay alive by default)
			if (pleaseStop) {
				stop();
				return;
			}
			mainLoopThread = new MainLoopThread(this);
			mainLoopThread.start();
			// ready
			readyFlag = true;
		} catch(Throwable ex) {
			ex.printStackTrace();
			Log.e(appName, ex); // make sure its logged
			throw Utils.runtime(ex);
		}
	}
	
	/**
	 * @return true once {@link #doMain(String[])} has completed 
	 */
	public boolean isReady() {
		return readyFlag;
	}

	/**
	 * Called after init() and before doMainLoop(). Override to do other main stuff.
	 * This method must return. To implement an infinite loop -- use doMainLoop();
	 * To avoid staying alive after this: call `stop()`
	 */
	protected void doMain2() {
		
	}
	
	/**
	 * Overide to do a loop. This gets called repeatedly. Use {@link #stop()} to stop.
	 * Any exceptions are caught, and the loop is restarted.
	 * <p>
	 * NB: This should always contains a Utils.sleep() command to stop it thrashing.
	 */
	protected void doMainLoop() throws Exception {
		Utils.sleep(20000); // loop on nothing e.g. to keep jetty alive
	}

	/**
	 * Calls init21_configFactory(), init2_config() then init2(config)
	 * @param args
	 */
	protected final void init(String[] args) {
		main = this;
		init2a_configFactory(args);
		if (config==null) { // normal (but allow setConfig() for tests)			
			config = init2_config(args);
		}
		init2(config);		
	}
	
	/**
	 * @deprecated For use in testing
	 * @param config
	 */
	public void setConfig(ConfigType config) {
		assert config != null;
		this.config = config;
		ManifestServlet.addConfig(config);		
		configRemainderArgs = new ArrayList();
	}
	
	private ConfigFactory init2a_configFactory(String[] args) {
		// make the ConfigFactory
		ConfigFactory cf = ConfigFactory.get();
		cf.setAppName(appName);
		if (args!=null) {
			cf.setArgs(args);
		}
		KServerType serverType = AppUtils.getServerType(null);
		cf.setServerType(serverType.toString());
		return cf;
	}
	
	/**
	 * TODO refactor so all AMains use this (poss overriding it)
	 */
	public final void init3_gson() {
		if (Dep.has(Gson.class)) {
			Log.w(appName, "init skip - Already done: Gson");
			return;
		}
		Gson gson = init4_gsonBuilder().create();
		Dep.set(Gson.class, gson);
	}

	protected GsonBuilder init4_gsonBuilder() {
		return new GsonBuilder()				
		.setLenientReader(true)
		.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter())
		.registerTypeAdapter(XId.class, new XIdTypeAdapter())
		.registerTypeHierarchyAdapter(AString.class, new StandardAdapters.ToStringSerialiser())
		.serializeSpecialFloatingPointValues()
		.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
//		.registerTypeAdapter(Throwable.class, new ThrowableLightAdapter())
//		.setClassProperty(null)
		.setLoopPolicy(KLoopPolicy.QUIET_NULL);
	}

	/**
	 * Convenience for {@link #init(String[])}, for use in unit tests.
	 * 
	 * This will initialise things like Gson and the database.
	 * It won't start Jetty.
	 */
	public final void init() {
		init(new String[0]);
	}
	
	/**
	 * Stop the app!
	 * Sets {@link #pleaseStop} to true, requests Jetty stop, and interrupts the {@link #mainLoopThread} if used.
	 */
	public void stop() {
		Log.i("AMain", "stop called "+ReflectionUtils.getSomeStack(8));
		pleaseStop = true;
		if (jl != null) {
			jl.stop();
			jl = null;
		}
		// break the loop, if we are in one
		if (mainLoopThread!=null && mainLoopThread.isAlive()) {
			mainLoopThread.interrupt();
			mainLoopThread = null;
		}
	}

	/**
	 * called after config has been loaded.
	 * This is the recommended method to override for custom init stuff.
	 * But do call super.init2()!
	 * 
	 * This base method does:
	 *  - App YA auth
	 *  - DataLog
	 *  - Emailer, via {@link #init3_emailer()}
	 *  
	 *  NOT (YET):
	 *  - TODO ES via {@link #init3_ES()}
	 *  - TODO gson via {@link #init3_gson()}
	 *  
	 * @param config
	 */
	protected void init2(ConfigType config) {
		if (initFlag) return;
		initFlag = true;		
		// init DataLog
		if (localDatalog) {
			DataLog.init();
			DataLog.getImplementation();
		} else {
			DataLog.init(false);
		}
		// YA - manual
//		init3_youAgain();
		// app auth - manual
//		init3_appAuth(config);
		// emailer
		try {
			init3_emailer();			
		} catch(Throwable ex) {
			// compact whitespace => dont spew a big stacktrace, so we don't scare ourselves in dev
			Log.e("init", StrUtils.compactWhitespace(Printer.toString(ex, true)));
			// oh well, no emailer
		}		
		// TODO init3_gson();
		// TODO init3_ES();
	}

	/**
	 * Not called by base class! Call in your apps overide, AFTER init3_youAgain()
	 * 
	 * Dep.set AuthToken, if we have login info
	 * @param config2
	 * @return token or null
	 */
	protected AuthToken init3_appAuth(ConfigType config2) {
		return AppUtils.initAppAuth(config2, getAppNameFull());
	}

	/**
	 * @return e.g. "myapp.mydomain.com"
	 */
	public String getAppNameFull() {
		return getAppNameLocal()+".good-loop.com";
	}

	/**
	 * Init the ES client and router (NOT any schemas/mappings)
	 * 
	 * Use {@link AppUtils#initESIndices(com.winterwell.data.KStatus[], Class[])}
	 * {@link AppUtils#initESMappings(com.winterwell.data.KStatus[], Class[], java.util.Map)}
	 */
	protected void init3_ES() {
		// config
		ESConfig esc = ConfigFactory.get().getConfig(ESConfig.class);
		// client
		ESHttpClient esjc = new ESHttpClient(esc);
		Dep.setIfAbsent(ESHttpClient.class, esjc);
		assert config != null;
		// Is the config the IESRouter?
		if (config instanceof IESRouter) {
			Dep.setIfAbsent(IESRouter.class, (IESRouter) config);
		} else {
			// nope - use a default
			Dep.setIfAbsent(IESRouter.class, new StdESRouter());
		}
		// Get the ES version to activate ES-version-specific code
		esjc.getESVersion();
		// ?? standard with dbclasses??
//		AppUtils.initESIndices(KStatus.main(), dbclasses);
//		AppUtils.initESMappings(KStatus.main(), dbclasses, mappingFromClass);
	}
	
	protected Emailer init3_emailer() {
		if (Dep.has(Emailer.class)) {
			return Dep.get(Emailer.class);		
		}
		EmailConfig ec = AppUtils.getConfig(appName, EmailConfig.class, null);
		Log.i("init", "Emailer with config "+ec);
		LoginDetails ld = ec.getLoginDetails();
		if (ld == null) {
			Log.w("init", "No Emailer: no login details");
			return null;
		}
		Emailer emailer = new Emailer(ec);
		Dep.set(Emailer.class, emailer);
		return emailer;
	}
	
	protected void init3_youAgain() {
		// idempotent
		if (Dep.has(YouAgainClient.class)) {
			return; // Dep.get(YouAgainClient.class);
		}
		// app=datalog for login
		String app = getAppNameLocal();
		// HACK
		String issuer = "good-loop"; // Have all of GL under one login??
		if (app.contains("sogive")) issuer = app;
		YouAgainClient yac = new YouAgainClient(issuer, app);
		Dep.set(YouAgainClient.class, yac);				
	}

	
	/**
	 * Initialise the _config_
	 * 
	 * Suggestion: use AppUtils.getConfig()
	 * @param args
	 * @return
	 */
	protected ConfigType init2_config(String[] args) {		
		Class ct = configType;
		if (ct==null) {
			ct = BasicSiteConfig.class;
			Log.w(getAppNameLocal(), "No ConfigType given - using "+ct.getSimpleName());
		}
		// HACK for embedded testing -- check Dep
		if (Dep.has(ct)) {
			Log.w(getAppNameLocal(), "init2_config() Using preset config");
			return (ConfigType) Dep.get(ct);
		}
		// make it
		ConfigFactory cf = ConfigFactory.get();
		if (args!=null) { // TODO remove this (its already been done)
			cf.setArgs(args);
		}
		Ref<List> rremainderArgs = new Mutable.Ref();
		ConfigType c;
		try {
			c = (ConfigType) cf.getConfig(ct, rremainderArgs);
		} catch(Exception ex) {
			// e.g. missing a required argument
			showHelp();
			throw Utils.runtime(ex);
		}
		
		Object r0 = Utils.isEmpty(rremainderArgs.value)? null : rremainderArgs.value.get(0); 
		if ("--help".equals(r0) || "-help".equals("r0")) {
			showHelp();
			System.exit(1);
		}
		
		// set them for manifest
		ManifestServlet.addConfig(c);
		assert c != null;
		configRemainderArgs = rremainderArgs.value;
		return c;		
	}
	
	protected void showHelp() {
		System.out.println("");
		System.out.println(appName);
		System.out.println("----------------------------------------");
		System.out.println("");
		try {
			ConfigBuilder cb = new ConfigBuilder(configType.getDeclaredConstructor().newInstance());
			System.out.println(cb.getOptionsMessage(null));
		} catch (Exception e) {
			System.err.println(e);
		}

	}
	

	protected void launchJetty() {
		try {
			Log.i("launchJetty - Go!");
			assert jl==null;
			jl = new JettyLauncher(getWebRootDir(), getPort());
			jl.setup();		
			// no sessions!
			WebRequest.setStateless(true);
			Log.d("AMain", "Call addJettyServlets in "+getClass());
			addJettyServlets(jl);
					
			Log.i("web", "...Launching Jetty web server on port "+jl.getPort());
			jl.run();		
			
			Log.i("Running... http://localhost:"+jl.getPort()+"/");
		} catch (Throwable ex) {
			// make sure it gets logged
			Log.e("launchJetty", ex);
			throw Utils.runtime(ex);
		}
	}

	/**
	 * Override! This should read from config
	 * @return
	 */
	protected final int getPort() {
		return getConfig().getPort();
	}

	/**
	 * TODO move this into ISiteConfig
	 * Override!
	 * @return
	 */
	protected File getWebRootDir() {
		return new File("web");
	}

	/**
	 * Adds /manifest and /testme
	 *
	 * Override! (but do call super) to set e.g. /* -> Master servlet
	 * Recommended code:
	 * 
	 * <pre><code>
		super.addJettyServlets(jl);
		MasterServlet ms = jl.addMasterServlet();	
		ms.add(MyServlet)
		</code></pre>
		
	 * @param _jl
	 */
	protected void addJettyServlets(JettyLauncher _jl) {
		Log.d("AMain", "Base addJettyServlets() in "+getClass());
		_jl.addServlet("/manifest", new HttpServletWrapper(ManifestServlet.class));
		// NB: not "test" cos there's often a test directory, and nginx gets confused
		_jl.addServlet("/testme/*", new HttpServletWrapper(TestmeServlet.class));		
	}


}

package com.winterwell.web.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.winterwell.bob.tasks.JarTask;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Printer;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.ArraySet;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.ConfigBuilder;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.ajax.JsonResponse;

/**
 * NB: Can be used directly or via {@link HttpServletWrapper}
 * @author daniel
 *
 */
public class ManifestServlet extends HttpServlet implements IServlet {

	private static final long serialVersionUID = 1L;

	private static final String LOGTAG = "manifest";

	private static volatile boolean initFlag;
	
	public ManifestServlet() {
		initManifest();
	}
	
	public static void initManifest() {
		if (initFlag) return;
		initFlag = true;
		// log config
		try {
			addConfig(Log.getConfig());
		} catch(Throwable ex) {
			Log.e("manifest", ex);
		}		
	}


	private static Time startTime = new Time();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			WebRequest state = new WebRequest(null, req, resp);
			process(state);
		} finally {
			WebRequest.close(req, resp);
		}
	}
	
	private static final ArraySet configs = new ArraySet();

	private static Map<String, Object> _versionProps;
	
	public static void addConfig(Object config) {
		// safety check
		Class<? extends Object> k = config.getClass();
		List old = Containers.filterByClass(configs, k);
		if (! old.isEmpty()) {
			Log.e(LOGTAG, "multiple configs: "+old+" vs "+config);
		}
		configs.add(config);		
	}
	
	public void process(WebRequest state) throws IOException {	
		Log.d(LOGTAG, "manifest request from "+state.getRemoteAddr());
		ArrayMap cargo = new ArrayMap();
		if (AMain.main!=null) {
			cargo.put("app", AMain.main.getAppNameFull());
			cargo.put("jetty-version", AMain.main.jl.getServer().getVersion());
		}
		
		// server type
		cargo.put("serverType", AppUtils.getServerType(null));
		
		ArrayList repos = new ArrayList();
		cargo.put("git-repos", repos);
		
		cargo.put("hostname", WebUtils2.hostname());
		
		String uptime = TimeUtils.toString(startTime.dt(new Time()));
		cargo.put("uptime", uptime);
		
		process2_versionProps(cargo);
		
		// origin -- maybe
		try {
			Properties props = Dep.get(Properties.class);
			String origin = props.getProperty("origin");
			if (origin == null) origin = "";
			cargo.put("origin", origin);
		} catch(Exception ex) {
			// oh well
		}		
		
		addConfigInfo(cargo);
		
		Map<String,Object> manifests = getJarManifests();		
		manifests = new TreeMap(manifests); // a-z sorting		
		cargo.put(("jarManifests"), manifests);
		
		try {
			cargo.put("servlets", AMain.main.jl.getServletMappings());
		} catch(Throwable ex) {
			Log.w("manifest", ex);
			// oh well
		}
		
		JsonResponse output = new JsonResponse(state, cargo);
		WebUtils2.sendJson(output, state);
	}

	/**
	 * See MakeVersionPropertiesTask
	 * @param cargo
	 */
	private static void process2_versionProps(ArrayMap cargo) {		
		File creolePropertiesForSite = new File("config", "version.properties");
		if ( ! creolePropertiesForSite.exists()) return;
		Map<String, Object> versionProps = getVersionProps();
		cargo.put("version", versionProps);
		// HACK
		String pubDate = (String) versionProps.get("publishDate");
		if (pubDate!=null) {
			cargo.put("version_published_date", new Time(pubDate).toString());
		}
	}

	/**
	 * Version Properties -- as made by Bob during the last build
	 * @return
	 */
	public static Map<String,Object> getVersionProps() {
		if (_versionProps != null) return _versionProps;
		File creolePropertiesForSite = new File("config", "version.properties");
		if ( ! creolePropertiesForSite.exists()) {
			_versionProps = new ArrayMap("error", "no version.properties file");
			return _versionProps;
		}
		try {
			Properties versionProps = FileUtils.loadProperties(creolePropertiesForSite);
			_versionProps = new ArrayMap(versionProps);		
			Object lct = _versionProps.get("lastCommit.time"); // HACK convert to Time
			if (lct != null) {
				_versionProps.put("lastCommit.time", new Time(lct.toString()));
			}
		} catch(Exception ex) {
			_versionProps = new ArrayMap("error", "version.properties: "+ex);
		}
		return _versionProps;
	}
	
	private void addConfigInfo(Map cargo) {
		// what did we load from?
		List<ConfigBuilder> cbs = ConfigFactory.get().getHistory();
		List<List<File>> cfs = Containers.apply(cbs, ConfigBuilder::getFileSources);
		Collection<File> configFiles = new HashSet(Containers.flatten(cfs));
		cargo.put("configFiles", configFiles);

		// what config did we pick up?
		// Screen for sensitive keys, e.g. passwords
		Map configsjson = new ArrayMap();
		ArraySet allConfigs = new ArraySet<>(configs);
		allConfigs.addAll(Containers.apply(cbs, ConfigBuilder::get));
		for(Object c : allConfigs) {
			ArrayMap<String, Object> vs = new ArrayMap(Containers.objectAsMap(c));
			for(String k : vs.keySet()) {
				boolean protect = ConfigBuilder.protectPasswords(k);
				if (protect) vs.put(k, "****");
			}
			// Handle in case there are two instances
			String klass = c.getClass().getSimpleName();
			String k = klass;
			int n = 1;
			while(configsjson.containsKey(k)) {
				n++;
				k = klass+n;
			}
			configsjson.put(k, vs);			
		}
		cargo.put("config", configsjson);
	}

	/**
	 * Info about the jars
	 * @return
	 */
	private Map<String, Object> getJarManifests() {
		ConcurrentMap<String, Object> manifestFromJar = new ConcurrentHashMap();
		try {		
			File dir = new File(FileUtils.getWorkingDirectory(), "build-lib");
			ExecutorService pool = Executors.newFixedThreadPool(10);
			File[] files = dir.listFiles();
			// hack - dev box?
			if (files==null && KServerType.LOCAL == AppUtils.getServerType(null)) {
				dir = new File(FileUtils.getWorkingDirectory(), "dependencies");
				files = dir.listFiles();
				if (files==null) {
					return manifestFromJar; // odd, but oh well
				}
			}	
			if (files==null) {
				Log.info("limited jar manifests - null files in "+dir);
				return manifestFromJar; // odd, but oh well
			}
			for (File file : files) {
				pool.submit(() -> {
					Map<String, Object> manifest = JarTask.getManifest(file);
					// reduce down to avoid bloat
					ArrayMap smallMainfest = new ArrayMap();
					for(String k : new String[] {"Implementation-Version", "Packaging-Date", "Bundle-Version"}) {
						Object v = manifest.get(k);
						if (v!=null) smallMainfest.put(k, v);
					}					
					manifestFromJar.put(file.getName(), smallMainfest);
				});	
			}
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.SECONDS);			
		} catch(Throwable ex) {
			Log.w(ex);
			manifestFromJar.put("error", Printer.toString(ex, true));
		}
		return manifestFromJar;
	}

	/**
	 * Add ver=lastCommit.time to mark the code version behind this event.
	 * @param event
	 * @return version-number or 0
	 */
	public static long setVersionGitCommitTime(DataLogEvent event) {
		Map<String, Object> m = getVersionProps();
		Object vert = m.get("lastCommit.time");
		if (vert instanceof Time) {
			long s = ((Time) vert).getTime();
			event.putProp("ver", s);	
			return s;
		} else {
			Log.d("ManifestServlet", "No version.properties lastCommit.time");
			return 0;
		}
	}
	
	
}

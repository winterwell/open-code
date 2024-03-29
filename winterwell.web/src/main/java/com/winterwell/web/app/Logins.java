package com.winterwell.web.app;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.ConfigBuilder;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.io.Option;
import com.winterwell.utils.log.Log;
import com.winterwell.web.LoginDetails;

/**
 * Good-Loop specific convenience for managing login details.
 * This looks up login details from logins/logins.misc.properties
 *  -- which is NOT in a public git repo.
 *  
 *  
 * YouAgain auth tokens: Use YouAgainClient.loadLocal()
 *  
 * Status: Low coverage. Most of our logins are NOT in here.
 *  
 * @author daniel
 *
 */
public class Logins {

	private static final String LOGTAG = "Logins";

	static final Logins dflt = init();
	
	private static File loginsDir;
	
	public static File getLoginsDir() {
		return loginsDir;
	}
	
	/**
	 * Init from the "normal" chain of X.properties -- if they have 
	 * properties logins.$domain.password, logins.$domain.loginName (see LoginDetails fields)
	 * + logins.misc.properties
	 * @return
	 */
	static Logins init() {
		ConfigBuilder cb = ConfigFactory.get().getConfigBuilder(Logins.class);
		loginsDir = new File(FileUtils.getWinterwellDir(), "logins");		
		File f = new File(loginsDir, "logins.misc.properties");
		if (f.isFile()) {
			cb.set(f);
		}
		Logins logins = cb.get(); // This allows the logins map to be populated from the properties
		Log.i(LOGTAG, "init credentials: "+logins.logins.keySet());
		return logins;
	}	
	
	/**
	 * 
	 * @param appName
	 * @param filename
	 * @return never null - might not exist
	 */
	public static File getLoginFile(String appName, String filename) {
		File f = new File(loginsDir, appName+"/"+filename);
		if ( ! f.isFile()) {
			Log.i(LOGTAG, "No credentials file: "+f);
		}
		Log.i(LOGTAG, "Found credentials file: "+f);
		return f;
	}
	
	@Option
	Map<String,String> logins = new HashMap();

	/**
	 * Logins loaded by {@link #init()}
	 * @param domain
	 * @return can be null
	 */
	public static LoginDetails get(String domain) {
		assert ! Utils.isBlank(domain);
		String _domain = domain.replace('.', '_');
		LoginDetails ld = new LoginDetails(domain);
		// what do we have?
		List<String> keys = Containers.filter(dflt.logins.keySet(), k -> k.startsWith(_domain));
		if (keys.isEmpty()) {
			File f = new File(loginsDir, "logins."+domain+".properties");
			if (FileUtils.isSafe(f.getName()) && f.isFile()) {
				// is it a set of logins.domain.X key/values?
				ConfigBuilder cb = ConfigFactory.get().getConfigBuilder(Logins.class);
				cb.set(f);
				Logins l2 = cb.get();
				for(String k2 : l2.logins.keySet()) {
					if ( ! dflt.logins.containsKey(k2)) {
						String v2 = l2.logins.get(k2);
						dflt.logins.put(k2, v2);
					}
				}
				// is it a LoginDetails object?
				ConfigBuilder cb2 = new ConfigBuilder(ld);
				cb2.set(f);
				LoginDetails ld2 = cb2.get();
				ld = ld2;
				// refilter the keys
				keys = Containers.filter(dflt.logins.keySet(), k -> k.startsWith(_domain));
			} else {
				return null;
			}
		}		
		for (String k : keys) {
			String f = k.substring(_domain.length()+1);
			ReflectionUtils.setPrivateField(ld, f, dflt.logins.get(k));
		}		
		return (LoginDetails) ld;
	}

	/**
	 * @param keyName
	 * @return value set by environment variable, or from a special folder `.env`
	 */
	public static String getKey(String keyName) {
		// a main property?? KEY=VALUE or --KEY VALUE
		ConfigFactory cf = ConfigFactory.get();
		
		// env variable?
		String ev = System.getenv(keyName);
		if (ev!=null) return ev;
		// a file?
		File f = new File(".env", FileUtils.safeFilename(keyName, false));
		if (f.isFile()) {
			return FileUtils.read(f).trim();
		}
//		System.out.println(f.getAbsolutePath());
		return null;
	}
	
}

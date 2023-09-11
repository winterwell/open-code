package com.winterwell.bob.wwjobs;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.winterwell.utils.Dep;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils;
import com.winterwell.web.app.ISiteConfig;
import com.winterwell.web.app.KServerType;

public class BuildHacks {

	/**
	 * local / test / production
	 */
	public static KServerType getServerType() {
		// cache the answer
		if (_serverType!=null) return _serverType;
		_serverType = getServerType2();
		Log.d("BuildHacks", "Using serverType "+_serverType);
		if (_serverType == null) { // paranoia, nov 2021
			Log.e("BuildHacks", "null serverType! "+ReflectionUtils.getSomeStack(8));
		}
		return _serverType;		
	}
	
	
	private static KServerType _serverType;
	private static String _hostname;

	/**
	 * Determined in this order:
	 *
	 * 1. Is there a config rule "serverType=dev|production" in Statics.properties?
	 * (i.e. loaded from a server.properties file)
	 * Is there a SERVER_TYPE environment variable?
	 * 2. Is the hostname in the hardcoded PRODUCTION_ and DEV_MACHINES lists?
	 *
	 * @return
	 */
	private static KServerType getServerType2() {		
		// ISiteConfig?
		if (Dep.has(ISiteConfig.class)) {
			ISiteConfig config = Dep.get(ISiteConfig.class);
			Object _st = config.getServerType2();
			KServerType st = (KServerType) _st;			
			if (st != null) return st;
		}		
		
		// explicit config ??who uses this?? is it for unit tests??
		if (Dep.has(Properties.class)) {
			String st = Dep.get(Properties.class).getProperty("serverType");
			if (st!=null) {
				Log.d("init", "Using explicit serverType "+st);			
				return KServerType.valueOf(st);
			} else {
				Log.d("init", "No explicit serverType in config");
			}
		} else {
			Log.d("init", "No Properties for explicit serverType");
		}
		
		// env variable?
		String envVar = System.getProperty("SERVER_TYPE");
		if (envVar != null) {
			try {
				KServerType st = KServerType.valueOf(envVar.toUpperCase().trim());
				return st;
			} catch(Throwable ex) {
				Log.w("BuildHacks", "(skip) Unrecognised environment variable SERVER_TYPE "+envVar);
			}			
		}
		
		// explicitly listed
		String hostname = getFullHostname();
		// HACK issue seen on enchantedtray May 2022
		if (hostname.endsWith(".home")) {
			hostname = hostname.substring(0, hostname.length()-5);
		}
		Log.d("init", "serverType for host "+hostname+" ...?");
		if (LOCAL_MACHINES.contains(hostname)) {
			Log.i("init", "Treating "+hostname+" as serverType = "+KServerType.LOCAL);
			return KServerType.LOCAL;
		}
		if (TEST_MACHINES.contains(hostname)) {
			Log.i("init", "Treating "+hostname+" as serverType = "+KServerType.TEST);
			return KServerType.TEST;
		}
		if (STAGE_MACHINES.contains(hostname)) {
			Log.i("init", "Treating "+hostname+" as serverType = "+KServerType.STAGE);
			return KServerType.STAGE;
		}

		Log.i("init", "Fallback: Treating "+hostname+" as serverType = "+KServerType.PRODUCTION);
		return KServerType.PRODUCTION;
	}

	private static final List<String> LOCAL_MACHINES = Arrays.asList(
			"enchantedtray", "stross", "aardvark", "burgess", "kornbluth", "butcher", "kai-goodloop",  
			"gravitas.vertexel.com", "pinkthad", "geoff-vm", "wingpad", "wingvm", "Lewis-GoodLoop", "vdurkin-gl",
			"michael-ThinkPad-T14-Gen-2i");
	private static final List<String> TEST_MACHINES = Arrays.asList(
			"hugh", "baker"
			);
	private static final List<String> STAGE_MACHINES = Arrays.asList(
			"stage"
			);
	
	public static String getFullHostname() {
		if (_hostname==null) _hostname = WebUtils.fullHostname();
		return _hostname;
	}

	public static String changeEndpointServerType(String endpoint, KServerType src) {
		// remove if present
		String _endpoint = endpoint.replaceFirst("local|test|stage", "");
		// and set again
		switch(src) {
		case PRODUCTION:
			break;
		case STAGE:
			_endpoint = _endpoint.replace("//", "//stage");
			break;
		case TEST:
			_endpoint = _endpoint.replace("//", "//test");
			break;
		case LOCAL:
			_endpoint = _endpoint.replace("//", "//local");
			break;
		}
		// HACK Sogive is different
		if (endpoint.contains("sogive")) {
			 switch(src) {
			 case PRODUCTION:
				_endpoint = "https://app.sogive.org/charity";
				break;
			 case STAGE: // ??
			 case TEST:
				 _endpoint = "https://test.sogive.org/charity";
				break;
			case LOCAL:	// https not working for local - DW Jan 2022
				_endpoint = "http://local.sogive.org/charity";
				break;
			}	 			 
		}
		return _endpoint;
	}
}

package com.winterwell.web.app;

import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.Option;

/**
 * 
 * @author daniel
 *
 */
public class BasicSiteConfig implements ISiteConfig {

	@Override
	public String toString() {
		return getClass().getSimpleName()+Containers.objectAsMap(this).toString();
	}


	@Option
	public int port = 8180; // Best to change this

	@Option public String appAuthPassword;
	
	/**
	 * Allows the server to verify itself with You-Again.
	 * 
	 * ??How to get this
	 */
	@Option String appAuthJWT;
	

	@Override
	public String getAppAuthPassword() {
		return appAuthPassword;
	}

	@Override
	public String getAppAuthJWT() {
		return appAuthJWT;
	}


	@Override
	public int getPort() {
		return port;
	}

	
	
}

package com.winterwell.web.app;

import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.Option;

/**
 * 
 * @author daniel
 *
 */
public class BasicSiteConfig implements ISiteConfig {

	/**
	 * See VersionString for a semantic-version utility
	 */
	@Option(description="(optional) label the code with a version number e.g. 1.2.0")
	public String version;
	
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

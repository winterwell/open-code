package com.winterwell.web.data;

import java.util.Map;

import com.winterwell.utils.containers.AbstractMap2;

/**
 * NB: For services like Twitter, which have different id spaces for users, messages, and DMs
 *  -- use multiple services, e.g. "twitter" for people (the default) vs "twitter.post" for posts.
 * @author daniel
 *
 * @param <Kind>
 */
public interface IDoCanonical {

	/**
	 * A no-op identity function.
	 */
	static final IDoCanonical DUMMY = new IDoCanonical() {
		@Override
		public String canonical(String name) {
			return name;
		}

		@Override
		public String getService() {
			return "dummy";
		}		
	};
	
	/**
	 * Use with {@link XId#setService2canonical(Map)} to allow XId to be used without
	 * initialising plugins.
	 */
	public static final Map<String, IDoCanonical> DUMMY_CANONICALISER = new AbstractMap2<String, IDoCanonical>() {
		@Override
		public IDoCanonical get(Object key) {
			return IDoCanonical.DUMMY;
		}
		@Override
		public IDoCanonical put(String key, IDoCanonical value) {
			return null;
		}
	};
	

	/**
	 * Used by XId to catch equivalent ids.
	 * @param name
	 * @return canonical form of this login name, e.g. lowercase
	 * @throws RuntimeException if the syntax is bad, e.g. "Alice" is not a valid email address. 
	 */
	String canonical(String name);

	/**
	 * The service this plugin relates to. E.g. "twitter"
	 * <p>
	 * This should only ever use the characters [a-zA-Z0-9-_.]
	 * and try to avoid using .
	 * E.g. "twitter", and "facebook" are preferred to "twitter.com"
	 * and "facebook.com". This is to avoid the potential for confusion
	 * with email (when we refer to spoonmcguffin@twitter) or web domains
	 * (which are typically related, but not the same).
	 */
	String getService();

}

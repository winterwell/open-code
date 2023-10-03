package com.goodloop.data;

/**
 * aka MediaType. 
 * 
 * NB: this file is in webappbase so that both adserver and greendata can use it
 * @author daniel
 *
 */
public enum KAdFormat {
	/** i.e. banner ads, though display is more obviously a generic term for a range of sizes */
	display, 
	video,
	social,
	audio;


	/**
	 * Convenience for valueOf
	 * @param object can be null
	 * @return can be null
	 */
	public static KAdFormat fromString(Object object) {
		if (object==null) return null;
		String s = object.toString().toLowerCase();
		
		// HACK convert some possible inputs??
		if ("image".equals(s)) {
			s = "display";
		}
		
		return valueOf(s);
	}
	
}

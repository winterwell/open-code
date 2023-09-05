package com.goodloop.data;

/**
 * aka MediaType
 * @author daniel
 *
 */
public enum KAdFormat {
	/** i.e. banner ads, though display is more obviously a generic term for a range of sizes */
	display, 
	video,
	social;


	/**
	 * Convenience for valueOf
	 * @param object can be null
	 * @return can be null
	 */
	public static KAdFormat fromString(Object object) {
		if (object==null) return null;
		String s = object.toString().toLowerCase();
		
		// HACK convert some possible inputs??
		
		return valueOf(s);
	}
	
}

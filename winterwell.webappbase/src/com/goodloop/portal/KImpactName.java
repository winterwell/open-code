package com.goodloop.portal;

import com.winterwell.utils.log.Log;

/**
 * Not actually an enum, but we do want to (flexibly) lock down the Impact.name values
 * @author daniel
 *
 */
public class KImpactName {
	
	public static final String CARBON_OFFSET = "carbon offset";
	
	public static final String TREES = "tree(s)";
	
	public static final String CORAL = "coral";
	
	public static final String MEALS = "meal(s)";

	public static String getName(String desc) {	
		if (desc==null) return null;
		// TODO something smarter
		desc = desc.toLowerCase();
		if (desc.contains("carbon")) {
			return CARBON_OFFSET;
		}
		if (desc.contains("tree")) {
			return TREES;
		}
		if (desc.contains("coral")) {
			return CORAL;
		}
		if (desc.contains("meal")) {
			return MEALS;
		}
		Log.d("KImpactName","unmatched: "+desc);
		return null;
	}
}

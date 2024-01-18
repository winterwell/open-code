package com.winterwell.data;

import java.util.Map;

public interface ISecurityByShares {

	/**
	 * As well as direct shares for this object, it can be shared by sharing of its owners
	 * E.g. a GreenTag can be shared via sharing access to the Agency.
	 * 
	 * This method returns the field-name for owners.
	 * 
	 * @return e.g. {Agency: agencyId, Advertiser: vertiser}
	 */
	Map<Class, String> getShareBy();

}

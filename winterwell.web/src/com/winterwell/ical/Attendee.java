package com.winterwell.ical;

import com.winterwell.utils.log.Log;

/**
 * TODO
 * @author daniel
 *
 */
public class Attendee {

	public String email;
	public String displayName;
	public String responseStatus;
	
	public String getEmail() {
		return email;
	}

	public Boolean isAttending() {
		if (responseStatus==null) return null;
		switch(responseStatus) {
		case "accepted": return true;
		case "declined": return false;
		}
		Log.d("ical", "TODO Attendee responseStatus "+responseStatus);
		return null;
	}
	
}

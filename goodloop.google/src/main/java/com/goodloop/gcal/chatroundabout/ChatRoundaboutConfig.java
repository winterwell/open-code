package com.goodloop.gcal.chatroundabout;

import com.winterwell.utils.io.Option;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.TimeOfDay;
import com.winterwell.web.app.ISiteConfig;

public class ChatRoundaboutConfig implements ISiteConfig {

	@Override
	public int getPort() {
		return 8713;
	}

	@Option
	TimeOfDay crossTeamTime = new TimeOfDay("1:40pm");

	// In GMT; 1pm GMT = 2pm BST
	@Option
	TimeOfDay inTeamTime = new TimeOfDay("1:30pm"); // Also all team

	@Option
	Dt duration = new Dt(10, TUnit.MINUTE);
	
	@Option
	String emailProperties = "logins/google.good-loop.com/email.properties";

	@Option(description = "If set, do not make any events - just report on what you would do, and who's blocked this week")
	boolean reportOnly;
}

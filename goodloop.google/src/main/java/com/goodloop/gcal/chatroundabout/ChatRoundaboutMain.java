package com.goodloop.gcal.chatroundabout;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import com.winterwell.utils.Utils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.web.app.AMain;

public class ChatRoundaboutMain extends AMain<ChatRoundaboutConfig> {
	
	private static final String FILENAME = "ChatRoundabout.txt";
	
	public static void main(String[] args) throws IOException {
		ChatRoundaboutMain mymain = new ChatRoundaboutMain();
        mymain.doMain(args);
	}
	
	public ChatRoundaboutMain() {
		super("chatroundabout", ChatRoundaboutConfig.class);
	}
	
	@Override
	protected void doMain2() {
		// ChatRoundabout should be idempotent within a given week.
		// A repeated call should (painstakingly) fail to make any events, because they already exist
		LocalDate _nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		Time nextFriday = new Time(_nextFriday.toString());
		System.out.println("Next Friday is: " + nextFriday);		
		
		String crLog;
		try {
			ChatRoundabout cr = new ChatRoundabout(getConfig(), ChatRoundabout.CHATSET_ALL);
			crLog = cr.run(nextFriday);
			
			if (config.reportOnly != true) try {
				cr.sendEmail(crLog, nextFriday, "wing@good-loop.com");
				cr.sendEmail(crLog, nextFriday, "daniel@good-loop.com");
			} catch (Exception ex) {
				Log.e("no.email", ex);
			}
		} catch (IOException e) {
			throw Utils.runtime(e);
		}	
		// let dan and wing know how it went
	
		pleaseStop = true;
	}
	
}

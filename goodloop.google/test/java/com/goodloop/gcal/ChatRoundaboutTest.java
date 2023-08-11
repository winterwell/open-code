package com.goodloop.gcal;

import java.util.List;

import org.junit.Test;

import com.goodloop.gcal.chatroundabout.ChatRoundabout;
import com.goodloop.gcal.chatroundabout.ChatRoundaboutConfig;
import com.goodloop.gcal.chatroundabout.Employee;
import com.winterwell.web.app.AMain;

public class ChatRoundaboutTest extends AMain<ChatRoundaboutConfig> {
	
	@Test
	public void testEmployee() {
		ChatRoundabout cr = new ChatRoundabout(getConfig(), "cross-team");
		List<Employee> emailList = cr.emailList();
		System.out.println(emailList.size());
	}
	
//	@Test
//	public void testGetHoildays() {
//		ChatRoundabout cr = new ChatRoundabout(getConfig(), "cross-team");
//		System.out.println(cr.getHolidays());
//	}
	
}

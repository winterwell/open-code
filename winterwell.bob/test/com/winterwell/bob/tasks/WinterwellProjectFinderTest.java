package com.winterwell.bob.tasks;

import java.io.File;

import org.junit.Test;

import com.winterwell.utils.StrUtils;
import com.winterwell.web.app.TestWebRequest;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.data.XId;

public class WinterwellProjectFinderTest {

	@Test
	public void testDevs() {
		// make the DEVS string
		// don't commit people's names to git (Dan's is OK as he chooses to allow it)
		String d = "";
		for(String s : "daniel".split(" ")) {
			d += StrUtils.sha1(s)+" ";
		}
		System.out.println(d);
		
		TestWebRequest state = new TestWebRequest();
		state.setUser(new XId("daniel@good-loop.com@email"), null);
		assert WinterwellProjectFinder.isDev(state);
	}

	
	@Test
	public void testApply() {
		WinterwellProjectFinder wpf = new WinterwellProjectFinder();
		File utils = wpf.apply("winterwell.utils");
		assert utils.isDirectory();
		assert utils.getName().equals("winterwell.utils");
	}


	@Test
	public void testCalstat() {
		WinterwellProjectFinder wpf = new WinterwellProjectFinder();
		File utils = wpf.apply("calstat");
		assert utils.isDirectory();
		assert utils.getName().equals("calstat");
	}

}

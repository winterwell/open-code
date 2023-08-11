package com.winterwell.datalog;

import org.junit.Test;

import com.winterwell.datalog.server.GeoLiteLocator;

public class GeoLiteLocatorTest {
	
	private GeoLiteLocator gll;

	void init() {
		if (gll!=null) return;
		// Instantiate GeoLiteLocator - will attempt to load CSVs and init prefix tree
		gll = new GeoLiteLocator();
		// Run GeoLiteUpdateTask - will check if CSVs are absent or out-of-date and init again if needed
		gll.blockUntilReady();
	}
	
	@Test
	public void testSmoke() {
		init();
		// Simple smoke test - Google public DNS should be in the US. 
		GeoLiteLocator.GeoIPBlock googleDNSLocation = gll.getLocation("8.8.8.8");
		assert "US".equals(googleDNSLocation.country);
		System.out.println("GeoLite2 thinks Google DNS (8.8.8.8) is in country code \"" + googleDNSLocation.country + "\"");
		
		// An IP that isn't a string of dot separated decimals will return null
		GeoLiteLocator.GeoIPBlock emptyIPLocation = gll.getLocation("");
		assert emptyIPLocation == null : "Empty IP gives country code \"" + emptyIPLocation.country + "\"";
		System.out.println("GeoLiteLocator was given a degenerate IP, returned: " + emptyIPLocation);
	}

	@Test
	public void testUK() {
		init();
		String ip = "95.146.44.100";		 
		GeoLiteLocator.GeoIPBlock location = gll.getLocation(ip);
		System.out.println(location);
		assert location.country.equals("GB");
	}
		
}

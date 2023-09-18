package com.winterwell.web;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.log.Log;

public class FakeBrowserTest {

	@Test
	public void testGetFile() {
		String url = "https://logo.clearbit.com/youtube.com";
		FakeBrowser fb = new FakeBrowser();
		File f = fb.getFile(url);
		System.out.println(f.getAbsolutePath());
		assert f.isFile();
		assert f.length() > 1000;
	}
	
//	@Test
	public void offtestISBN() {
		FakeBrowser fb = new FakeBrowser();
		String p = fb.getPage("https://isbnsearch.org/search?s=time+travellers");
		assert p.contains("egency") : p;
		
		String p2 = fb.getPage("https://isbndb.com/search/books/time%2Btravellers%2Bwife");
		assert p2.contains("Wife") : p2;
	}
	
	@Test
	public void testMethodHead() {
		{
			FakeBrowser fb = new FakeBrowser();
			fb.setRequestMethod("HEAD");
			String beeb = fb.getPage("https://www.bbc.co.uk");			
			Map<String, List<String>> rh = fb.getResponseHeaders();
			assert beeb.isEmpty();
			assert ! rh.isEmpty();
//			System.out.println(rh);
		}
	}


	@Test
	public void testHeadersOnly() {
		FakeBrowser fb = new FakeBrowser();
		String url = "https://www.bbc.co.uk";
		fb.getHeadersOnly(url);
		Map<String, List<String>> rh = fb.getResponseHeaders();
		assert ! rh.isEmpty();
	}
		
	@Test
	public void testSetRetryOnError() {
		{	// simple unset
			FakeBrowser fb = new FakeBrowser();
			String beeb = fb.getPage("https://bbc.co.uk");
			assert ! beeb.isEmpty();
		}
		{	// simple set
			FakeBrowser fb = new FakeBrowser();
			fb.setRetryOnError(3);
			String beeb = fb.getPage("https://bbc.co.uk");
			assert ! beeb.isEmpty();
		}
		try {	// fail unset
			FakeBrowser fb = new FakeBrowser();
			String beeb = fb.getPage("https://bbcdadsasda.dadsada");
			assert false;
		} catch(Exception ex) {
			assert ex.toString().contains("UnknownHost") : ex.toString();
		}
		try {	// fail set
			FakeBrowser fb = new FakeBrowser();
			fb.setRetryOnError(3);
			String beeb = fb.getPage("https://dasdasda.ewadsas");
			assert false;
		} catch(Exception ex) {
			assert ex.toString().contains("UnknownHost");
		}
	}
	
	@Test
	public void testCurl() {
		{	// simple unset
			StringBuilder slog = new StringBuilder();
			Log.addListener(report -> slog.append(report.toString()));
			FakeBrowser fb = new FakeBrowser();
			fb.setRequestHeader("XMyHeader", "whatever");
			fb.setUserAgent(null);
			fb.setDebug(true);
			String beeb = fb.getPage("https://bbc.co.uk", new ArrayMap("foo", "bar", "msg", "Hello World!"));
			assert slog.toString().contains("foo");
		}
	}

}

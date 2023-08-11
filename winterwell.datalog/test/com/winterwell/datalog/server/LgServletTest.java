package com.winterwell.datalog.server;

import java.io.IOException;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.winterwell.datalog.DataLogConfig;
import com.winterwell.utils.Dep;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.app.BrowserType;

import ua_parser.Client;
import ua_parser.Parser;

public class LgServletTest {

	private static int port;
	private static DataLogServer dls;
	
	/**
	 * Run a datalog server for the tests to hit
	 * TODO also a testcontainers ES to point it at?
	 */
	@BeforeClass
	public static void startServer() {
		Dep.setIfAbsent(DataLogConfig.class, new DataLogConfig());
		port = Dep.get(DataLogConfig.class).port;
		dls = new DataLogServer();
		dls.doMain(null);
	}
	
	/**
	 * Teardown the datalog server cleanly
	 */
	@AfterClass
	public static void stopServer() {
		dls.stop();
	}

	@Test
	public void testLgLocal() {
		FakeBrowser fb = new FakeBrowser();
		Map<String, String> vars = new ArrayMap<>(
			"d", "green",
			"t", "pxl",
			"domain", "ampproject.net",
			"url", "https://www.foxbusiness.com/lifestyle/pesto?recipe=share",
			"ow", true // overwrite
		);
		fb.setRequestHeader("Referer", "https://a4c191f013ff56ad49cb7d214e89ecec.safeframe.googlesyndication.com/");
		String got = fb.getPage("http://127.0.0.1:"+port+"/lg", vars);
		System.out.println(got);
		JSend<?> jsend = JSend.parse(got);
		Map<?, ?> event = jsend.getDataMap();
		System.out.println(event);
		assert event.get("domain").equals("foxbusiness.com") : event;
	}

	@Test
	public void testUTM() {
		Map<String, Object> params = new ArrayMap<>();		
		String ref = "https://www.drynites.co.uk/?utm_source=mysauce&utm_medium=clairvoyant&utm_campaign=freedom";
		LgServlet.readGoogleAnalyticsTokens(ref, params);
		assert params.get("source").equals("mysauce");
		assert params.get("medium").equals("clairvoyant");
		assert params.get("campaign").equals("freedom");
	}

	@Test
	public void testMalformedUTM() {
		Map<String, Object> params = new ArrayMap<>();		
		String ref = "https://www.drynites.co.uk/?utm_source=&utm_campaign";
		LgServlet.readGoogleAnalyticsTokens(ref, params);
		LgServlet.readGoogleAnalyticsTokens("", params);
		assert params.isEmpty();
	}

	@Test
	public void testIsTechSite() {
		assert AdTechUtils.isTechSite("googlesyndication.com");
		assert AdTechUtils.isTechSite("adnxs-simple.com");
		assert ! AdTechUtils.isTechSite("mail.com");
		assert ! AdTechUtils.isTechSite("");
		assert ! AdTechUtils.isTechSite(null);
	}
	
	@Test
	public void testBrowserType() {
		String ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36";
		BrowserType bi = LgServlet.getBrowserInfo(ua);
		assert bi.getBrowserMake().equals("chrome");
		assert bi.getOS().equals("linux");
		assert bi.getVersion()==83.0D;
		assert !bi.isMobile();
	}
	
	@Test
	public void testHttp() {		
		FakeBrowser fb = new FakeBrowser();
		fb.setDebug(true);
		String url = "http://127.0.0.1:"+port+"/lg?t=testevent&d=gl&p=%7B%22pub%22%3A%22www.good-loop.com%22%2C%22bid%22%3A%22bid_ycnlix161420c7431%22%2C%22vert%22%3A%22vert_ikbqiuqf%22%2C%22campaign%22%3A%22Lifecake%22%2C%22variant%22%3A%7B%22adsecs%22%3A15%2C%22banner%22%3A%22default%22%2C%22unitSlug%22%3A%22lifecake%22%7D%2C%22slot%22%3A%22glad0%22%2C%22format%22%3A%22mediumrectangle%22%7D&r=&s=";
		String ok = fb.getPage(url);
		assert ok.startsWith("{\"messages\":[],\"cargo\":{\"count\":1.0,\"dataspace\":\"gl\",\"evt\":[\"testevent\"],\"id\":\"gl_testevent_");
	}

	@Test
	public void testParser() throws IOException {
		Parser p = LgServlet.uaParser();
		Client c = p.parse("Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36");
		assert "Chrome 67".equals(c.userAgent.family+" "+c.userAgent.major);
	}
}

package com.winterwell.utils.web;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.time.TUnit;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.WebPage;
import com.winterwell.web.app.JettyLauncher;
import com.winterwell.web.app.WebRequest;

import eu.medsea.mimeutil.MimeUtil;

public class WebUtils2Test {


	@Test
	public void testStripScripts() {
		String s = WebUtils2.stripScripts("MyGL \"><img src=x onerror=javascript:alert(document.cookie) x=\"");
		System.out.println(s);
		String s2 = WebUtils2.stripTags("MyGL \"><img src=x onerror=javascript:alert(document.cookie) x=\"");
		System.out.println(s2);
	}


	@Test
	public void testStringify() {
		Assert.assertEquals("\"foo\"", WebUtils2.stringify("foo"));
		Assert.assertEquals("{\"foo\":1}", WebUtils2.stringify(new ArrayMap("foo",1)));
//		Assert.assertEquals("{\"foo\":1}", WebUtils2.stringify(new MyPOJO())); // POJOs dont work - use gson
		// or this for shallow pojo handling
		Assert.assertEquals("{\"foo\":1}", WebUtils2.stringify(Containers.objectAsMap(new MyPOJO())));
	}

	
	@Test
	public void testOpenCalLink() {
		WebUtils2.browseOnDesktop("https://meet.google.com/uri-pqhs-kzu");
	}

	@Test
	public void testAddToList() {
		String json0 = "{\"a\":[]}";
		Map jobj0 = WebUtils2.parseJSON(json0);
		SimpleJson.set(jobj0, "apple", "a", "1");
		assert "apple".equals(SimpleJson.get(jobj0, "a", 1));
	}

	@Test
	public void testWhoIs() throws Exception {
	// WhoisClient whois = new WhoisClient(); commons-net -- not good, tested Linux 2020
		String ip = "82.37.168.255";
		Map vals = WebUtils2.whois(ip);
		System.out.println(vals);
		assert vals.get("country").equals("GB");
	}


	
	
	@Test
	public void testResolveRedirectsGoogle() {
		String gu = "https://www.google.com/url?rct=j&sa=t&url=https://www.weareumi.co.uk/news/sectors/creative-media/good-loop-secures-soap-glory-campaign-against-hygiene-poverty&ct=ga&cd=CAEYAioUMTQ4MzAwMjk0NzczNDc4MTk5NDUyGjQ4ZmEyZmZhY2M4OTUyZGU6Y29tOmVuOlVT&usg=AFQjCNFFs6KqQS9eWjEW_mLxZClfiKostg;";
		String u = WebUtils2.resolveRedirectsInUrl(gu, null);
		assert ! u.contains("google.com") : u;
		assert u.equals("https://www.weareumi.co.uk/news/sectors/creative-media/good-loop-secures-soap-glory-campaign-against-hygiene-poverty") : u;
	}
	
	@Test
	public void testCleanUp() {
		{
			String u = WebUtils2.cleanUp("http://bbc.co.uk/foo=bar&um=whatever");
			assert u.equals("http://bbc.co.uk/foo=bar&um=whatever");
		}
		{
			String ref = "https://www.drynites.co.uk/?utm_source=mysauce&utm_medium=clairvoyant&utm_campaign=freedom";
			String u = WebUtils2.cleanUp(ref);
			assert u.equals("https://www.drynites.co.uk/") : u;
		}
		{	// malformed
			String ref = "https://www.drynites.co.uk/?utm_source=&utm_medium=Hello World&utmcampaign=freedom";
			String u = WebUtils2.cleanUp(ref);
			assert u.equals("https://www.drynites.co.uk/?utmcampaign=freedom") : u;
		}
		{
			String ref = "";
			String u = WebUtils2.cleanUp(ref);
			assert u.equals("") : u;
		}
	}

	@Test
	public void testAddCookie() {
		JettyLauncher jl = new JettyLauncher(FileUtils.getWorkingDirectory(), 8961);
		jl.addServlet("/dummy", new DummyServlet());
		jl.run();
		
		FakeBrowser fb = new FakeBrowser();
		String foo = fb.getPage("http://localhost:8961/dummy");
		Map<String, String> cookies = fb.getHostCookies("localhost");
		Printer.out(cookies);
		assert cookies.containsKey("foo");
	}

		
	@Test // MimeUtil v 1.3. works -- v2.1 breaks
	public void testGetMimeType_uncommon() {
//		MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
		File f = new File("test/more.txt");
		assert f.isFile() : f.getAbsolutePath();
		Collection mts = MimeUtil.getMimeTypes(f);
		Printer.out(mts);
		Object mt = Containers.first(mts);
		assert mt.toString().startsWith("text/") : mt;
	}
	
	@Test
	public void testQuotedPrintableEncodeDecode() {
		String qp = "<div align=3D\"center\" >=09=09Hello!</div>";
		String plain = WebUtils2.decodeQuotedPrintable(qp);
		String enc = WebUtils2.encodeQuotedPrintable(plain);
		assert plain.equals("<div align=\"center\" >		Hello!</div>") : plain;
		assert enc.equals(qp) : enc;
	}

	
	@Test
	public void testXmlDocToString() {
		String xml = "<foo blah='whatever'><bar src=''>huh?</bar></foo>";
		Document doc = WebUtils2.parseXml(xml);
		String xml2 = WebUtils2.xmlDocToString(doc);
		String c = StrUtils.compactWhitespace(xml2.trim()).replaceAll("[\r\n]", "").replace('"', '\'');
		// chop xml version string
		int i = c.indexOf("?>");
		if (i!=-1) c = c.substring(i+2, c.length());
		assert c.equals(xml) : xml+" -> "+c;
	}

	@Test
	public void testCanonicalEmailString() {
		{
			String e = WebUtils2.canonicalEmail("Bob <Bob@FOO.COM>");
			assert e.equals("bob@foo.com");
		}
		{
			String e = WebUtils2.canonicalEmail("Alice.1@FOO.bar.co.uk");
			assert e.equals("alice.1@foo.bar.co.uk");
		}
	}
	
	@Test
	public void testGetDomain() {
		assert WebUtils2.getDomain("www.cbsnews.com").equals("cbsnews.com");
		assert WebUtils2.getDomain("us.thetaxcalculator.net").equals("thetaxcalculator.net");
		assert WebUtils2.getDomain("bulbapedia.bulbagarden.net").equals("bulbagarden.net");
	}

}


class DummyServlet extends HttpServlet {			
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		WebRequest state = new WebRequest(req, resp);
		state.setCookie("foo", "bar", TUnit.MINUTE.dt, null);
		WebPage wp = new WebPage();
		wp.sb().append("Hello World");
		state.setPage(wp);
		state.sendPage();
	}
	
}

class MyPOJO {
	int foo = 1;
}
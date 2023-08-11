package com.winterwell.datalog.server;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Test;

import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogRemoteStorage;
import com.winterwell.datalog.Dataspace;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.app.TestWebRequest;
import com.winterwell.web.app.WebRequest;
import com.winterwell.youagain.client.YouAgainClient;

public class DataServletTest {

	@Test
	public void testSecurityChecker() {
		Dep.set(new YouAgainClient(DATASPACE, "test.good-loop.com"));
		DataServlet ds = new DataServlet();
		WebRequest state = new TestWebRequest();
		boolean no = ds.glUserChecker.check(state);
		assert ! no;
	}
	
	private static final CharSequence DATASPACE = new Dataspace("testspace");
	private static DataLogServer server;
	private static DataLogConfig config;
	private String ENDPOINT;

	public void initDataTest() {
		if (config!=null) return;
		// spin up a server
		server = new DataLogServer();
		server.doMain(new String[0]);
		ENDPOINT = "http://localhost:"+server.getConfig().getPort();

		// poke some data in
		// ...pixel tracking data
		new FakeBrowser().getPage(ENDPOINT+"/pxl?host=example.com&domain=test.example.com");		
		// ...event data
		DataLogEvent event = new DataLogEvent(DATASPACE, null, 1, new String[]{"unittest"}, new ArrayMap(
			"user", "DataServletTest@bot",
			"stage", "init",
			"host", "localhost"
		));
		DataLogRemoteStorage.saveToRemoteServer(event);
		// pause for ES to save
		Utils.sleep(1000);
	}
	

	@Test
	public void testSafeUserInfo() {
		initDataTest();
		FakeBrowser fb = fb();		
		String json = fb.getPage(ENDPOINT+"/data", new ArrayMap(
				"name","test-user",
				"dataspace", DATASPACE,
				"breakdown", "{\"user\":\"cardinality\"}"
				));
		JSend resp = JSend.parse(json);
		String data = resp.getData().string();
		Printer.out(data);
		assert ! data.contains("no0");
	}
	
	
	@Test
	public void testBreakdowns() {
		initDataTest();
		FakeBrowser fb = fb();		
		String json = fb.getPage(ENDPOINT+"/data", new ArrayMap(
				"name","test-1",
				"dataspace", DATASPACE,
				"breakdown", "evt/time,evt"
				));
		JSend resp = JSend.parse(json);
		String data = resp.getData().string();
		Printer.out(data);
		assert ! data.contains("no0");
	}
	

	@Test
	public void testSort() {
		initDataTest();
		FakeBrowser fb = fb();		
		String json = fb.getPage(ENDPOINT+"/data", new ArrayMap(
				"name","test-2",
				"dataspace", "test", //DATASPACE,
				"breakdown", "evt {\"count\": \"sum\"}",
				"sort", "desc"
				));
		JSend resp = JSend.parse(json);
		String data = resp.getData().string();
		Printer.out(data);
		assert ! data.contains("no0");
	}
	
	
	@Test
	public void testOps() {
		initDataTest();
		FakeBrowser fb = fb();		
		String json = fb.getPage(ENDPOINT+"/data", new ArrayMap(
				"name","test-2",
				"dataspace", DATASPACE,
				"breakdown", "evt {\"count\": \"sum\"}"
				));
		JSend resp = JSend.parse(json);
		String data = resp.getData().string();
		Printer.out(data);
		assert ! data.contains("no0");
	}
	
	@Test
	public void testOpsWithoutBreakdown() {
		initDataTest();
		FakeBrowser fb = fb();		
		String json = fb.getPage(ENDPOINT+"/data", new ArrayMap(
				"name","test-3",
				"dataspace", DATASPACE,
				"breakdown", "{\"dntn\": \"sum\"}"
				));
		JSend resp = JSend.parse(json);
		String data = resp.getData().string();
		Printer.out(data);
		assert ! data.contains("no0");
		Map map = resp.getDataMap();
		Object dntnSum = map.get("dntn");
		assert dntnSum instanceof Number;
	}
	
	@AfterClass
	public static void close() {
		server.stop();
	}

	private FakeBrowser fb() {
		FakeBrowser fb = new FakeBrowser();
		fb.setDebug(true);
		return fb;
	}

}

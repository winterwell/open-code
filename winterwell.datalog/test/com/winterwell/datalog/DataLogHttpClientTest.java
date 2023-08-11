package com.winterwell.datalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.DepContext;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class DataLogHttpClientTest {

	private static DepContext context;


	@BeforeClass
	public static void beforeTests() {
		context = Dep.setContext("DataLogHttpClientTest");
		DataLogConfig dlc = ConfigFactory.get().getConfig(DataLogConfig.class);
		
		// test or local?
//		dlc.dataEndpoint = "https://testlg.good-loop.com/data";
//		dlc.logEndpoint = "https://testlg.good-loop.com/lg";
		
		dlc.dataEndpoint = "https://locallg.good-loop.com/data";
		dlc.logEndpoint = "https://locallg.good-loop.com/lg";
		
		Dep.set(DataLogConfig.class, dlc);
	}
	
	@AfterClass
	public static void afterTests() {
		context.close();
	}

	@Test
	public void testUnindexed() {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("testunindexed"));
		dlc.setDebug(true);
		dlc.getConfig().debug = true;
		dlc.initAuth("good-loop.com");
		assert dlc.getConfig().logEndpoint.contains("testlg") 
			|| dlc.getConfig().logEndpoint.contains("locallg") : dlc.getConfig().logEndpoint;
		Map<String, ?> props = new ArrayMap(
			"huh", 1,
			"os", "Dummy OS"
		);
		String gby = "gby_testsave_unindexed_1";
		Map<String, ?> unindexed = new ArrayMap("foo","bar", 
				"mappy", new ArrayMap(
						"listy", "a b c".split(" "), 
						"x", 17
						)
				);
		DataLogEvent event = new DataLogEvent(dlc.dataspace, gby, 1, new String[] {"testsave1u"}, props, unindexed);
		event.setTime(new Time().minus(TUnit.WEEK));
		Object ok = dlc.save(event);
		System.out.println(ok);
		Utils.sleep(2000);
		dlc.setPeriod(new Time().minus(TUnit.MONTH), null);
		List<DataLogEvent> events = dlc.getEvents(new SearchQuery("evt:testsave1u"), 10);
		System.out.println(events);
		assert ! events.isEmpty();

	}

	
	@Test
	public void testGetEvents() {
		// get all
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		List<DataLogEvent> events = dlc.getEvents(null, 5);
		System.out.println(events);
		assert events.size() > 0;
	}
	

	@Test
	public void testGetPagedEvents() {
		// get all		
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		dlc.initAuth("good-loop.com");
		dlc.setDebug(true);
//		List<AuthToken> auths = getAuth();
//		dlc.setAuth(auths);
		Map paging = new HashMap();		
		List<DataLogEvent> events = dlc.getEvents(null, paging);
		System.out.println(events);
		System.out.println(paging);
		
		// go again
		List<DataLogEvent> events2 = dlc.getEvents(null, paging);
		System.out.println(events2);
		System.out.println(paging);		
	}
	
//
//	private List<AuthToken> getAuth() {
//		DataLogHttpClient.ini
//
//		public static void testGetIdentityToken() {
//			YouAgainClient yac = new YouAgainClient(YouAgainClient.MASTERAPP);
//			App2AppAuthClient a2a = new App2AppAuthClient(yac);
//			String appAuthName = "uploadclippings"+".good-loop.com";
//			String appAuthPassword = null;
//			AuthToken token = a2a.registerIdentityTokenWithYA(appAuthName, appAuthPassword);
//			yac.storeLocal(token);
//			System.out.println(token.getXId());
//			System.out.println(token);
//			assert token != null;
//		}
//		
//		AuthToken auth = AppUtils.initAppAuth(new DataLogConfig(), "testlg.good-loop.com");		
//		List<AuthToken> auths = Arrays.asList(auth);
//
//	}

	@Test
	public void testBreakdown() {
		// get all
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		SearchQuery q = new SearchQuery("evt:spend");
		Breakdown breakdown = new Breakdown("pub", "count", KBreakdownOp.sum);
		Map<String, Double> events = dlc.getBreakdown(q, breakdown);
		System.out.println(events);
		assert ! events.isEmpty();
	}

	
	@Test
	public void testBreakdownCount() {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("gl"));
		dlc.initAuth("good-loop.com");
		assert dlc.isAuthorised();
		SearchQuery sqd = new SearchQuery("evt:donation");
		List<DataLogEvent> donEvents = dlc.getEvents(sqd, 10);
		// NB: the count field is always present on DataLogEvents
		Breakdown bd = new Breakdown("cid", "count", KBreakdownOp.sum);
		Map<String, Double> dontnForAdvert = dlc.getBreakdown(sqd, bd);	
		System.out.println(dontnForAdvert);
	}
	

	@Test
	public void testSave() {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("test"));
		dlc.setDebug(true);
		dlc.initAuth("good-loop.com");
		assert dlc.getConfig().logEndpoint.contains("testlg") 
			|| dlc.getConfig().logEndpoint.contains("locallg") : dlc.getConfig().logEndpoint;
		Map<String, ?> props = new ArrayMap(
			"huh", 1,
			"os", "Dummy OS"
		);
		String gby = "gby_testsave_1";
		DataLogEvent event = new DataLogEvent(dlc.dataspace, gby, 1, new String[] {"testsave1"}, props);
		event.setTime(new Time().minus(TUnit.WEEK));
		Object ok = dlc.save(event);
		System.out.println(ok);
		Utils.sleep(2000);
		dlc.setPeriod(new Time().minus(TUnit.MONTH), null);
		List<DataLogEvent> events = dlc.getEvents(new SearchQuery("evt:testsave1"), 10);
		System.out.println(events);
		assert ! events.isEmpty();
	}
	
}

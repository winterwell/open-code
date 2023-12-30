package com.winterwell.datalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.winterwell.datalog.DataLog.KInterpolate;
import com.winterwell.datalog.server.DataLogFields;
import com.winterwell.maths.stats.distributions.d1.IDistribution1D;
import com.winterwell.maths.timeseries.IDataStream;
import com.winterwell.utils.Dep;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Pair2;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.Actor;
import com.winterwell.utils.threads.IFuture;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.StopWatch;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.IHasJson;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.FakeBrowser;

/**
 * 
 * Normally use DataLogHttpClient as a front-end on this.
 * 
 * This is a kind of DatalogClient API class TODO Remote server storage for
 * DataLog So the adserver can log stuff into lg.
 * 
 * @author daniel
 * @testedby DataLogRemoteStorageTest
 */
public class DataLogRemoteStorage implements IDataLogStorage {

	/**
	 * a direct call to the remote server
	 * 
	 * @param event
	 * @return
	 */
	public static String saveToRemoteServer(DataLogEvent event) {
		return saveToRemoteServer(event, getConfig());
	}

	/**
	 * a direct call to the remote server
	 * @param event
	 * @return
	 */
	public static String saveToRemoteServer(DataLogEvent event, DataLogConfig configForRemote) {
		assert event != null;
		// TODO via Dep
		DataLogRemoteStorage dlrs = new DataLogRemoteStorage();
		// add https and endpoint
		assert configForRemote.logEndpoint.contains("/lg") : configForRemote.logEndpoint;
		dlrs.init(configForRemote);

		// DEBUG July 2018
		String eds = event.dataspace;
		if (event.dataspace == null) {
			Log.e("datalog", "null dataspace?! " + event);
			eds = "gl"; // paranoia HACK
		}

		Dataspace ds = new Dataspace(eds);
		// save
		Object ok = dlrs.saveEvent(ds, event, new Period(event.time));
		Log.d("datalog.remote", "Save queued to " + configForRemote.logEndpoint + " " + event + " response: " + ok);
		return (String) ok;
	}

	private static DataLogConfig getConfig() {
		DataLogConfig config = Dep.getWithConfigFactory(DataLogConfig.class);
		return config;
	}

	private String logEndpoint;
	private String getDataEndpoint;
	private boolean debug;

	@Override
	public IDataLogStorage init(DataLogConfig settings) {
		logEndpoint = settings.logEndpoint;
		getDataEndpoint = settings.dataEndpoint;
		debug = settings.debug;
		saveActor.setDebug(debug);
		return this;
	}

	@Override
	public void save(Period period, Map<String, Double> tag2count, Map<String, IDistribution1D> tag2mean) {
		Collection<DataLogEvent> events = new ArrayList();
		for (Entry<String, Double> tc : tag2count.entrySet()) {
			DataLogEvent event = new DataLogEvent(tc.getKey(), tc.getValue());
			events.add(event);
		}
		for (Entry<String, IDistribution1D> tm : tag2mean.entrySet()) {
			IDistribution1D distro = tm.getValue();
			DataLogEvent event = new DataLogEvent(tm.getKey(), distro.getMean());
			if (distro instanceof IHasJson) {
				// paranoid defensive copy
				ArrayMap json = new ArrayMap(((IHasJson) distro).toJson2());
				event.setExtraResults(json);
			}
			events.add(event);
		}
		saveEvents(events, period);
	}

	@Override
	public void saveHistory(Map<Pair2<String, Time>, Double> tag2time2count) {
		for (Entry<Pair2<String, Time>, Double> tc : tag2time2count.entrySet()) {
			DataLogEvent event = new DataLogEvent(tc.getKey().first, tc.getValue());
			event.time = tc.getKey().second;
			// Minor TODO batch for efficiency
			Collection<DataLogEvent> events = new ArrayList();
			events.add(event);
			DataLogImpl dl = (DataLogImpl) DataLog.getImplementation();
			Period bucketPeriod = dl.getBucket(event.time);
			saveEvents(events, bucketPeriod);
		}
	}

	@Override
	public IFuture<IDataStream> getData(Pattern id, Time start, Time end) {
		// TODO Auto-generated method stub
		throw new TodoException();
	}

	@Deprecated // TODO! parse the output. Unify with DataLogHttpClient
	@Override
	public StatReq<IDataStream> getData(String tag, Time start, Time end, KInterpolate fn, Dt bucketSize) {
		FakeBrowser fb = fb();
		fb.setDebug(debug);
//		fb.setAuthentication("daniel@local.com", "1234");		// FIXME remove this into options!
		Map<String, String> vars = new ArrayMap("q", "evt:" + tag, "breakdown", "time");
		vars.put("d", DataLog.getDataspace());
		vars.put("t", DataLogEvent.simple); // type
		String res = fb.getPage(getDataEndpoint, vars);
		Object jobj = WebUtils2.parseJSON(res);
		throw new TodoException(jobj);
	}

	static private FakeBrowser fb() {
		FakeBrowser fb = new FakeBrowser();
		fb.setDebug(getConfig().isDebug());
		fb.setUserAgent(FakeBrowser.HONEST_USER_AGENT);
		return fb;
	}

	@Override
	public StatReq<Double> getTotal(String tag, Time start, Time end) {
		// TODO Auto-generated method stub
		throw new TodoException();
	}

	@Override
	public Iterator getReader(String server, Time start, Time end, Pattern tagMatcher, String tag) {
		// TODO Auto-generated method stub
		throw new TodoException();
	}

	@Override
	public IFuture<MeanRate> getMean(Time start, Time end, String tag) {
		// TODO Auto-generated method stub
		throw new TodoException();
	}

	@Override
	public StatReq<IDataStream> getMeanData(String tag, Time start, Time end, KInterpolate fn, Dt bucketSize) {
		// TODO Auto-generated method stub
		throw new TodoException();
	}

	@Override
	public void setHistory(Map<Pair2<String, Time>, Double> tagTime2set) {
		// TODO Auto-generated method stub
		if (Utils.isEmpty(tagTime2set))
			return;
		Log.w(new TodoException(tagTime2set));
	}

	@Override
	public String saveEvent(Dataspace dataspace, DataLogEvent event, Period periodIsNotUsedHere) {
		assert dataspace.equiv(event.dataspace) : dataspace + " v " + event;
		saveActor.send(new SaveDataLogEvent(this, event));
		return "queued";
	}

	static final DataLogRemoteStorageActor saveActor = new DataLogRemoteStorageActor();

	/**
	 * msg to pass into the save-actor
	 */
	final static record SaveDataLogEvent(DataLogRemoteStorage dlrs, DataLogEvent event) {
	}
	
	/**
	 * Use an actor thread for low latency to the caller
	 * @author daniel
	 *
	 */
	static class DataLogRemoteStorageActor extends Actor<SaveDataLogEvent> {
		@Override
		protected void consume(SaveDataLogEvent se, Actor from) throws Exception {
			// See LgServlet which reads these
			FakeBrowser fb = fb();
			fb.setTimeOut(10000); // log should be quick
			fb.setRetryOnError(5); // try a few times to get through. Can block for 2 seconds.
			String[] tags = se.event.getEventType();
			for (String tag : tags) { // ??why one call per tag??
				Map<String, Object> vars = new ArrayMap();
				// core fields
				vars.put(DataLogFields.d.name, se.event.dataspace);
				vars.put("gby"/* LgServlet.GBY.name */, se.event.groupById); // group it?			
				vars.put(DataLogFields.t.name, tag); // type
				vars.put("count", se.event.count);
				// time accuracy: to the second (not millisecond??)
				Time time = se.event.getTime();
				String ts = time==null? null : time.toISOString(); // paranoia! time should always be set here
				vars.put("time", ts);
				// debug?
				if (debug) {
					vars.put("debug", debug);
				}
				// props
				String p = WebUtils2.stringify(se.event.getProps());
				vars.put("p", p);
				// ...unindexed
				if (se.event.unindexed !=null) { 	
					Map<String, Object> unindexed = se.event.unindexed;
					String s = new SimpleJson().toJson(unindexed);
					vars.put("unindexed", s); // assume json-friendly format
				}
				
				// Overwrite (as opposed to update)?
				if (se.event.overwrite) {
					vars.put("ow",  1);
				}
				
				// TODO String r = referer
				String res = fb.post(se.dlrs.logEndpoint, (Map) vars);
				Log.d("datalog.remote", "called " + fb.getLocation() + " return: " + res);				
			}
		}
	}
	
	public static boolean isQueueEmpty() {
		return saveActor.isIdle();
	}
	
	/**
	 * Block until the q is consumed. Beware that this could block forever if fresh messages keep coming.
	 * @see BatchActor#join()
	 */
	public void joinWhenIdle() {
		saveActor.joinWhenIdle();
	}

	@Override
	public void saveEvents(Collection<DataLogEvent> events, Period period) {
		// TODO use a batch-save for speed
		for (DataLogEvent e : events) {
			saveEvent(new Dataspace(e.dataspace), e, period);
		}
	}

	/**
	 * 
	 * @param maxMsecs
	 * @return true if queue cleared, false if max-time
	 */
	public static boolean waitFor(int maxMsecs) {
		int m = (int) (maxMsecs/10);
		for(int i=0; i<m; i++) {
			if (isQueueEmpty()) {
				return true;
			}
			Utils.sleep(10);
		}
		Log.w("lg", "waitFor save timed out with queue : "+saveActor.getQ().size());
		return false;
	}


}

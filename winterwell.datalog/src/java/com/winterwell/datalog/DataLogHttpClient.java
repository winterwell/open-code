package com.winterwell.datalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.winterwell.datalog.server.DataLogFields;
import com.winterwell.maths.stats.distributions.d1.MeanVar1D;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.FailureException;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.ConfigException;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.ajax.KAjaxStatus;
import com.winterwell.web.data.XId;
import com.winterwell.youagain.client.App2AppAuthClient;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.LoginFailedException;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * Warning: This has state variables like start/end -- so beware of using
 * it across eg servlets. 
 * 
 * Get data via the DataLog web service (i.e. call DataServlet)
 * 
 * @see DataLogRemoteStorage
 * 
 * @author daniel
 * @testedby  DataLogHttpClientTest
 */
public class DataLogHttpClient {

	private static final String LOGTAG = "DataLogHttpClient";

	public final Dataspace dataspace;
	
	final DataLogConfig config;
	
	private List<AuthToken> auth;
	
	/**
	 * (create) and store locally an auth token
	 * @param thisAppName the domain name
	 * @return
	 */
	public DataLogHttpClient initAuth(String thisAppName) {
		YouAgainClient yac = new YouAgainClient("lg.good-loop", thisAppName);
		App2AppAuthClient a2a = yac.appAuth();
		XId appXid = App2AppAuthClient.getAppXId(thisAppName);
		AuthToken at = yac.loadLocal(appXid);
		if (at!=null) {
			setAuth(Arrays.asList(at));	
			return this;
		}
		// try to init!
		Log.d(LOGTAG, "No stored auth token for "+appXid);
		String thisAppPassword = config.endpointPassword;
		if (Utils.isBlank(thisAppPassword)) {
			throw new ConfigException("No DataLog endpointPassword for "+appXid, "lg.good-loop.com");
		}
		try {
			at = a2a.getIdentityTokenFromYA(thisAppName, thisAppPassword);
		} catch(LoginFailedException ex) {
			// This may be a 404
			at = a2a.registerIdentityTokenWithYA(thisAppName, thisAppPassword);
			Log.d(LOGTAG, "Registered auth token for "+appXid);
		}
		assert at != null;
		Log.d(LOGTAG, "Success! Storing auth token for "+appXid);
		yac.storeLocal(at);
		setAuth(Arrays.asList(at));			
		return this;
	}
	
	public DataLogConfig getConfig() {
		return config;
	}
	
	/**
	 * @deprecated count of docs -- NOT the sum of values
	 */
	private transient Double allCount;

	private List<Map> examples;

	private Time start;

	private Time end;
	
	private boolean debug = true;

	/**
	 * @deprecated for debug - the last url fetched
	 */
	private transient String lastCall;
	
	@Override
	public String toString() {
		return "DataLogHttpClient[dataspace=" + dataspace+"]";
	}

	public DataLogHttpClient(Dataspace namespace) {
		this(namespace, Dep.getWithDefault(DataLogConfig.class, new DataLogConfig()));
	}
	
	public DataLogHttpClient(Dataspace namespace, DataLogConfig config) {
		this.config = config;
		assert config != null;
		assert config.logEndpoint.startsWith("http") : config.logEndpoint;
		assert config.logEndpoint.contains("/lg") : config.logEndpoint;
		this.dataspace = namespace;
		Utils.check4null(namespace);
		assert ! namespace.toString().contains(".") : "server / namespace mixup? "+namespace;
	}
	
	
	/**
	 * Save to remote lg server
	 * @param event
	 * @return
	 */
	public Object save(DataLogEvent event) {
		return DataLogRemoteStorage.saveToRemoteServer(event, config);
	}
	
	/**
	 * @return true if all save events have been carried out
	 */
	public boolean isQueueEmpty() {
		return DataLogRemoteStorage.isQueueEmpty();
	}
	
	/**
	 * 
	 * @param q
	 * @param maxResults
	 * @return Can this return null?? when does that happen??
	 * @throws WebEX.E401 You have to have setup auth (see {@link #initAuth(String)}) to get events data. 
	 */
	public List<DataLogEvent> getEvents(SearchQuery q, int maxResults) throws WebEx.E401 {
		return getEvents2(q, maxResults, null);
	}
	

	private static final int PAGE_SIZE = 10000;

	/**
	 * 
	 * @param q
	 * @param paging ??what does this hold?? Is it "collect the response from a previous page"??
	 * @return
	 * @throws WebEx.E401
	 */
	public List<DataLogEvent> getEvents(SearchQuery q, Map paging) throws WebEx.E401 {
		return getEvents2(q, PAGE_SIZE, paging);
	}
	
	List<DataLogEvent> getEvents2(SearchQuery q, int maxResults, Map paging) throws WebEx.E401 {
		if (auth==null || auth.isEmpty()) {
			throw new WebEx.E401("getEvents requires auth. Call setAuth() first.");
		}
		// Call DataServlet		
		Map<String, String> vars = new ArrayMap(
				"dataspace", dataspace, 
				"q", q==null? null : q.getRaw(), 
				"size", maxResults,
				"paging", paging==null? null : WebUtils2.stringify(paging),
				DataLogFields.START.name, startParam(), // ?? why not use null if unset? Wouldnt that be a bit faster in ES
				DataLogFields.END.name, end==null? null : end.toISOString(),
				"debug", debug
				);
		// call
		JSend jsend = get2_httpCall(vars);

		if (jsend.getStatus()==KAjaxStatus.error || jsend.getStatus()==KAjaxStatus.fail) {
			throw new FailureException(jsend.getMessage());
		}
		
		Map jobj = jsend.getDataMap();
		
		// update paging?
		if (paging!=null) {
			paging.clear();
			Map newPaging = (Map) jobj.get("paging");
			if (newPaging != null) {				
				paging.putAll(newPaging);
			} else {
				Log.e(LOGTAG, "No new paging for "+paging+" of "+q);
			}
		}
		
		List<Map> egs = Containers.asList(jobj.get("examples"));
		if (egs==null || egs.isEmpty()) {
			String m = jsend.getMessage();
			// HACK
			if (m !=null && m.contains("text=Not logged in => no examples")) {
				throw new WebEx.E401(config.dataEndpoint, "Call DataLogHttpClient.setAuth() first "+m);
			}
			return (List) egs;
		}

		List<DataLogEvent> des = new ArrayList();
		// Convert into DataLogEvents
		for (Map eg : egs) {
			String id = (String) eg.get("_id");
			DataLogEvent de = DataLogEvent.fromESHit(dataspace, id, (Map)eg.get("_source"));
			des.add(de);
		}
		return des;
	}

	private String startParam() {
		// NB: the /data endpoint defaults to start=1 month ago
		return start==null? TimeUtils.WELL_OLD.toISOString() : start.toISOString();
	}	

	public DataLogHttpClient setAuth(List<AuthToken> auth) {
		assert auth==null || ! auth.contains(null) : auth;
		this.auth = auth;
		return this;
	}
	
	/** As a bit of security (cos examples carry more data than aggregate stats), we default to 0 */
	int numExamples = 0;

	private Map<String,MeanVar1D> statsFor;
	
	public void setNumExamples(int numExamples) {
		this.numExamples = numExamples;
	}

	/**
	 * 
	 * TODO refactor for greater flex
	 * 
	 * Side effects: set examples
	 * @param q
	 * @param breakdown
	 * @return {key e.g. "oxfam": value e.g. 100} 
	 * Warning: if the breakdown had null by (e.g. you wanted a total) then this is empty.
	 * But you can call getTotalFor() to fetch the result
	 */
	public Map<String, Double> getBreakdown(SearchQuery q, Breakdown breakdown) {
		return (Map) getBreakdown2(q, breakdown);
	}
	

	/**
	 * Same as {@link #getBreakdown(SearchQuery, Breakdown)} but for multiple fields
	 * (e.g. emissions -> co2 co2base co2creative)
	 * @param q
	 * @param breakdown
	 * @return The nested-map structure is the difference from getBreakdown()
	 */
	public Map<String, Map<String, Double>> getBreakdownMultiField(SearchQuery q, Breakdown breakdown) {
		return (Map) getBreakdown2(q, breakdown);
	}
	
	/**
	 * 
	 * @param q
	 * @param breakdown
	 * @return field:double or map,double if multiple breakdowns
	 */
	Map<String, Object> getBreakdown2(SearchQuery q, Breakdown breakdown) {
		// Call DataServlet		
		JSend jobj = getBreakdown2_fetch(q, breakdown);		
		
		Map jobjMap = jobj.getDataMap();
		
		// e.g. by_cid buckets
		// FIXME handle multi-level breakdown e.g. "agency/campaign"
		List<Map> buckets = new ArrayList();		
		for(String byi : breakdown.by) { // NB: this loop will be a no-op for by=null=[""]
			// NB: can include byi="" for top-level total, but that will be null here (there's no "by_") - handled later
			List byi_buckets = Containers.asList((Object)SimpleJson.get(jobjMap, "by_"+byi, "buckets"));
			if (byi_buckets==null) {
				byi_buckets = Containers.asList((Object)SimpleJson.get(jobjMap, "sampling", "by_"+byi, "buckets"));
			}
			if (byi_buckets != null) buckets.addAll(byi_buckets);
		}		
		Map<String, Object> byX = new ArrayMap();
		// convert it		
		for (Map bucket : buckets) {
			String k = (String) bucket.get("key");
			String[] fields = breakdown.getFields();
			ArrayMap<String, Double> byF = new ArrayMap();			
			for (String f : fields) {
				Object ov = bucket.get(f);
				if (ov instanceof Map) {	// HACK old code, Jan 2021
					ov = ((Map)ov).get(breakdown.op);
				}
				double v = MathUtils.toNum(ov);
				byF.put(f, v);
			}
			// HACK the return type depends on one field vs many
			if (fields.length==1) {
				var v = byF.first().second;
				byX.put(k, v);
			} else {
				byX.put(k, byF);
			}
		}			
		// ...count of docs
		Object _allCount = SimpleJson.get(jobjMap, ESDataLogSearchBuilder.allCount);
		if (_allCount instanceof Map) {	// HACK old code, Jan 2021
			_allCount = ((Map)_allCount).get("count");
		}
		allCount = MathUtils.toNum(_allCount);
		
		// ...total
		totalFor = new ArrayMap();
		// e.g. .cid -- ??How is this ever set?? Does this code work??
		for(String byi : breakdown.by) {if (byi=="") continue;
			Object _btotal = SimpleJson.get(jobjMap, byi);
			if (_btotal == null) {
				Log.d(LOGTAG, "No top-by total?! "+byi+" "+jobjMap);
				continue;
			}
			double bttl = MathUtils.toNum(_btotal);
			totalFor.put(byi, bttl);
			Log.w(LOGTAG, "Yes top-by total "+byi+" "+jobjMap);
		}	
		// e.g. sum of "dntn"
		statsFor = new ArrayMap();
		for(String f : breakdown.getFields()) {
			Object _bf = SimpleJson.get(jobjMap, f);
			if (_bf != null) {
				Object _btotal = _bf;
				if (_bf instanceof Map) {
					_btotal = ((Map)_btotal).get("sum");
				}
				double bttl = MathUtils.toNum(_btotal);
				totalFor.put(f, bttl);
			}
			// stats?
			if (breakdown.op == KBreakdownOp.stats || breakdown.op==KBreakdownOp.avg) {
				Map _bstats = (Map)_bf;
				_bstats.put("mean", _bstats.get("avg")); // HACK
				MeanVar1D mv = new MeanVar1D(_bstats);
				statsFor.put(f, mv);
			}
		}

		// ...examples
		examples = Containers.asList((Object)SimpleJson.get(jobjMap, "examples"));
		
		return byX;
	}

	private JSend getBreakdown2_fetch(SearchQuery q, Breakdown breakdown) {
		String b = breakdown==null? null : breakdown.toString();		
		ArrayMap vars = new ArrayMap(
				"dataspace", dataspace,				
				"q", q.getRaw(), 
				"breakdown", b,
				DataLogFields.START.name, startParam(),
				DataLogFields.END.name, end==null? null : end.toISOString(),
				"prb", randomSamplingProb,
				"size", numExamples);
		
		// Call!
		JSend jobj = get2_httpCall(vars);
		return jobj;
	}
	
	
	/**
	 * 
	 * Side effects: set examples
	 * @param q
	 * @param breakdown Must be a single breakdown.by (which can have multiple slices). E.g. 
	 * breakdown.by = ["pub/vertiser/time"]
	 * @return e.g. [pub, vertiser, time, count] 
	 */
	public List<Object[]> getBreakdownTable(SearchQuery q, Breakdown breakdown) {
		JSend jobj = getBreakdown2_fetch(q, breakdown);

		Map jobjMap = jobj.getDataMap();
		
		// e.g. by_cid buckets
		String[] headers = breakdown.by;
		if (breakdown.by.length == 1) {
			if (breakdown.by[0].contains("/")) {
				Log.e("(handled) Needed to split breakdown.by "+breakdown);
				headers = breakdown.by[0].split("/");
			}
//		throw new IllegalArgumentException(Printer.str(breakdown.by)+" Must be a single breakdown.by (which can have multiple slices). E.g. breakdown.by = [\"pub/vertiser/time\"]");
		};
		// HACK "emissions" and countco2 expands
		String[] bfield = breakdown.fields;
		if (bfield.length==1 && breakdown.getField().equals(ESDataLogSearchBuilder.COUNTCO2)) {
			bfield = "count co2".split(" ");
		}
		// unpack the data
		List<Object[]> rows = getBreakdownTable2(headers, 0, jobjMap, bfield);
		// enlarge the headers
		String[] headers2 = Arrays.copyOf(headers, headers.length+bfield.length);
		for(int i=0; i<bfield.length; i++) {
			headers2[headers.length + i] = bfield[i];
		}
		rows.add(0, headers2);

		// ...count of docs
		Object _allCount = SimpleJson.get(jobjMap, ESDataLogSearchBuilder.allCount);
		if (_allCount instanceof Map) {	// HACK old code, Jan 2021
			_allCount = ((Map)_allCount).get("count");
		}
		allCount = MathUtils.toNum(_allCount);
		
		// e.g. sum of "dntn", or TODO (not working!) multi-field sum of [count, co2]
		for(String field : breakdown.getFields()) {
			Object _btotal = SimpleJson.get(jobjMap, field);
			if (_btotal != null) {
				double bttl = MathUtils.toNum(_btotal);
				totalFor.put(field, bttl);
			} else {
				Log.w(LOGTAG, "No total found for "+field+" from "+breakdown); // maybe we need to look deeper into jobj??
			}
		}

		// ...examples
		examples = Containers.asList((Object)SimpleJson.get(jobjMap, "examples"));
		
		return rows;
	}
	
	private List<Object[]> getBreakdownTable2(String[] headers, int i, Map jobjMap, String[] bfield) {
		String h = headers[i];
		String byX = "by_"+StrUtils.join(Arrays.copyOfRange(headers, i, headers.length), "_");
		Map resultsByX = (Map) jobjMap.get(byX);
		if (resultsByX==null) {
			resultsByX = SimpleJson.get(jobjMap, "sampling", byX);
		}
		List rows= new ArrayList();
		List<Map> buckets = SimpleJson.getList(resultsByX, "buckets");
		if (buckets==null) {
//			System.out.println(i); // ran out of data early in the tree??
			return rows;
		}		
		for (Map bucket : buckets) {			
			Object key = bucket.get("key");
			if (i==headers.length-1) {
				Object[] keyRow = new Object[headers.length + bfield.length];
				keyRow[i] = key;
				for(int bi=0; bi<bfield.length; bi++) {
					String field = bfield[bi];
					Object cnt = bucket.get(field);
					// NB: the rest of the row is set on the recursion out										
					keyRow[i+1+bi] = cnt;					
				}
				rows.add(keyRow);
			} else {
				List<Object[]> keyRows = getBreakdownTable2(headers, i+1, bucket, bfield);
				for (Object[] keyRow : keyRows) {
					assert keyRow[i] == null;
					keyRow[i] = key;
				}
				rows.addAll(keyRows);
			}
		}
		return rows;
	}

	/**
	 * The totals
	 * {breakdown-by-KEY: total}
	 */
	Map<String,Double> totalFor = new ArrayMap();


	private Double randomSamplingProb;

	/**
	 * Call DataLog for data!
	 * NB: Can be over-ridden to implement a cache
	 * @param vars
	 * @return
	 */
	protected JSend get2_httpCall(Map vars) {		
		FakeBrowser fb = new FakeBrowser();
		fb.setUserAgent(FakeBrowser.HONEST_USER_AGENT);
		fb.setDebug(debug);		
		fb.setRetryOnError(2); // 3 tries
		fb.setMinRetryPause(new Dt(100, TUnit.MILLISECOND));
		// auth!
		if (auth!=null) {
			AuthToken.setAuth(fb, auth);
		}	
		// use a post() in case vars is large -- eg a giant query
		String json = fb.post(config.dataEndpoint, vars);		
		lastCall = fb.getLocation();
		
		JSend jobj = JSend.parse(json);
		if ( ! jobj.isSuccess()) {
			throw new FailureException(jobj.getMessage());
		}
		return jobj;
	}

	/**
	 * stached from the previous {@link #getBreakdown(SearchQuery, Breakdown)} call
	 * @return
	 */
	public List<DataLogEvent> getExamples() {
		if (examples==null) return null;
		List<DataLogEvent> des = new ArrayList();
		// Convert into DataLogEvents
		for (Map eg : examples) {
			String id = (String) eg.get("_id");
			DataLogEvent de = DataLogEvent.fromESHit(dataspace, id, (Map)eg.get("_source"));
			des.add(de);
		}
		return des;
	}

	/**
	 * 
	 * @param start If null, defaults to well-old (i.e. all)
	 * @param end If null, defaults to now
	 */
	public void setPeriod(Time start, Time end) {
		this.start = start; 
		this.end = end;
	}
	
	/**
	 * @deprecated This is usually NOT the number you want.
	 * @return How many docs were included in the results?
	 * @see #getTotalFor()
	 */
	public Double getAllCount() {
		return allCount;
	}
	
	/**
	 * 
	 * @return {breakdown-by-key: total} E.g. {cid:100, vert:100}
	 */
	public Map<String, Double> getTotalFor() {
		return totalFor;
	}

	public Map<String, MeanVar1D> getStatsFor() {
		return statsFor;
	}
	
	public String getLastCall() {
		return lastCall;
	}
	
	// Set whether the client's FakeBrowsers should run in debug mode
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public boolean isAuthorised() {
		return auth != null && ! auth.isEmpty();
	}

	/**
	 * 
	 * @param p -1 for auto
	 */
	public void setRandomSamplingProbability(double p) {
		randomSamplingProb = p;
	}


}

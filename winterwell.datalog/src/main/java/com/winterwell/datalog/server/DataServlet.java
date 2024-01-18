package com.winterwell.datalog.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import com.google.common.cache.CacheBuilder;
import com.winterwell.bob.tasks.WinterwellProjectFinder;
import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.DataLogImpl;
import com.winterwell.datalog.DataLogSecurity;
import com.winterwell.datalog.Dataspace;
import com.winterwell.datalog.ESDataLogSearchBuilder;
import com.winterwell.datalog.ESStorage;
import com.winterwell.datalog.KBreakdownOp;
import com.winterwell.es.StdESRouter;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.CSVSpec;
import com.winterwell.utils.io.CSVWriter;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.ICallable;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.utils.web.XStreamUtils;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.AjaxMsg;
import com.winterwell.web.ajax.AjaxMsg.KNoteType;
import com.winterwell.web.ajax.JsonResponse;
import com.winterwell.web.app.CommonFields;
import com.winterwell.web.app.CrudServlet;
import com.winterwell.web.app.IServlet;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.app.WebRequest.KResponseType;
import com.winterwell.web.fields.Checkbox;
import com.winterwell.web.fields.DoubleField;
import com.winterwell.web.fields.DtField;
import com.winterwell.web.fields.EnumField;
import com.winterwell.web.fields.IntField;
import com.winterwell.web.fields.JsonField;
import com.winterwell.web.fields.SField;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * Serves up aggregations data.
 * 
 * size: number of examples
 * numRows: max terms in the breakdown
 * breakdown: e.g. evt/time 
 * 	See {@link ESDataLogSearchBuilder} for more info on the breakdown syntax

 * 
 * @author daniel
 * @testedby  DataServletTest
 */
public class DataServlet implements IServlet {
	
	public static final Checkbox LOCALSHARD = new Checkbox("localshard");

	/**
	 * Number of results in aggregations
	 */
	private static final IntField numRows = new IntField("numRows");
	private static final IntField SIZE = new IntField("size");
	/**
	 * @deprecated move to [0,1] -- but with backwards compatability
	 */
	private static final IntField PROB = new IntField("prob");
	/**
	 * Probability in [0,1]
	 */
	public static final DoubleField PRB = new DoubleField("prb");
	
	private static final IntField SIG_FIG = new IntField("sigfig");
	
	@Deprecated /* let's standardise on `d` for input and query */
	public static final SField DATASPACE = new SField("dataspace");
	

	/**
	 * Current format: "desc"|"asc" and it always applies to time.
	 * 
	 * TODO Update to match the fieldname-desc/asc format used by {@link CrudServlet#SORT}
	 */
	private static final SField SORT_EXAMPLE = new SField("sortExample");
	
	private static final String LOGTAG = "DataServlet";
	/**
	 * Any truthy value (e.g. the empty map) will do to start paging.
	 * Then collect the return value and use that in the next request.
	 */
	private static final JsonField PAGING = new JsonField("paging");

	private static final ConcurrentMap<String,Map> cacheAggregations = (ConcurrentMap) CacheBuilder.newBuilder()
			.expireAfterWrite(20, TimeUnit.MINUTES)
			.maximumSize(500)
			.build().asMap();

	private static final Checkbox FIXSEED = new Checkbox("fixseed");
	/**
	 * This affects day-bucketing (e.g. for daylight-savings). It does NOT affect how dates are interpreted.
	 */
	private static final SField BUCKET_TIMEZONE = new SField("btz"); 

	private static final EnumField<KBreakdownOp> OP = new EnumField<>(KBreakdownOp.class,"op");
		
	/**
	 * Returns a (slightly cleaned) ES output 
	 */
	@Override
	public void process(WebRequest state) throws IOException {										
		Dataspace d = state.get(DataLogFields.d);
		if (d==null) d = new Dataspace(state.get(DATASPACE, "default"));
		// Uses "paths" of breakdown1/breakdown2/... {field1:operation, field2}
		List<String> breakdown = state.get(DataLogFields.breakdown);
		if (breakdown==null) {
//			Log.w(LOGTAG, "You want data but no breakdown?! Default to time. "+state);
			breakdown = new ArrayList();
			breakdown.add("time");
		}
		// remove `none` if present (which is to block the default)
		breakdown.remove("none");
		// HACK add in the op? (which can/should also be sent as part of breakdown, cos that can specify the property+op)
		KBreakdownOp op = state.get(OP);
		if (op!=null && op!=KBreakdownOp.sum) {
			List<String> breakdownPlusOp = Containers.apply(breakdown, b -> b+="{\"count\":\""+op+"\"}");
			breakdown = breakdownPlusOp;
		}
		
		// security: on the dataspace, and optionally on the breakdown
		DataLogSecurity.check(state, d, breakdown);

		// num results
		int numTerms = state.get(numRows, 1000);		
		Map paging = null;
		Object _paging = state.get(PAGING);
		if (Utils.truthy(_paging)) {
			paging = _paging instanceof Map? (Map) _paging : new ArrayMap();
		}
		
		// num examples
		int size = state.get(SIZE, paging==null? 10 : 1000);
		boolean isGLUser = glUserChecker.check(state); // Move into DataLogSecurity for clarity
		// ONLY give examples for dev 
		if (size > 0 && ! isGLUser) {
			size = 0;
			state.addMessage(new AjaxMsg(KNoteType.info, "401", "Not logged in => no examples"));
			if (paging != null) {
				throw new WebEx.E401(null, "Not logged in => cant page over examples");
			}
		}
		
		// random sampling?
		Double probability = state.get(PRB);
		boolean fixedRandomSeed = state.get(FIXSEED); 
		Integer _prob = state.get(PROB);
		if (probability==null) {			
			if (_prob!=null) {
				probability = _prob/100.0;
			}
		}
		
		// Return significant figures
		Integer sigFig = state.get(SIG_FIG);
		
		// time window		
		// HACK: quantized for the cache
		ICallable<Time> cstart = state.get(DataLogFields.START);
		Time start = cstart==null? TimeUtils.getStartOfDay(new Time().minus(TUnit.WEEK)) : cstart.call();
		Time end;
		// NB: special handling to quantize "now" for caching
		String _end = state.get(DataLogFields.END.getName());
		if (_end==null || "now".equals(_end)) {
			Dt quant = new Dt(10, TUnit.MINUTE);
			Time now = new Time();
			long nowish = quant.getMillisecs() * (now.getTime() / quant.getMillisecs());
			end = new Time(nowish);
		} else {
			ICallable<Time> cend = DataLogFields.END.fromString(_end);
			end = cend.call();
		}
		// We'd like the default to be [inc-start:true, inc-end:false)
		// so that a series of queries exactly parcel up the data
		// BUT this is a change (March 2023)! So beware of bugs!
		// NB: motivated by the include-too-much bug https://good-loop.monday.com/boards/2603585504/views/60487313/pulses/4070182568
		boolean incStart = state.get(DataLogFields.INCSTART, true);
		boolean incEnd = state.get(DataLogFields.INCEND, false);
		
		// TODO distribution data??
//		ess.getMean(start, end, tag);
		
		// query e.g. host:thetimes.com
		String q = state.get("q");
		if (q==null) q = "";
		SearchQuery filter = new SearchQuery(q);				

		Dt interval = state.get(new DtField("interval"), TUnit.DAY.dt);
				
		DataLogImpl dl = (DataLogImpl) DataLog.getImplementation();
		ESStorage ess = (ESStorage) dl.getStorage();
//		ESStorage ess = Dep.get(ESStorage.class);
		
		
		String timezone = state.get(BUCKET_TIMEZONE);
		
		ESHttpClient esc = ess.client(d);		

		// collect all the info together
		ESDataLogSearchBuilder essb = new ESDataLogSearchBuilder(esc, d);		
		essb.setBreakdown(breakdown)
			.setQuery(filter)
			.setNumResults(numTerms)
			.setNumExamples(size)
			.setStart(start).setIncStart(incStart)
			.setEnd(end).setIncEnd(incEnd)
			.setRandomSampling(probability, fixedRandomSeed)
			.setBucketTimezone(timezone);
			
		essb.setInterval(interval);		
		// paging? Any truthy value will do to start paging!
		if (paging != null) {
			essb.setPaging(paging);
		}

		// sort buckets?
		// FIXME This does NOT currently follow the CrudServlet.SORT fielname-asc|desc format
		String sortBucketBy = state.get(CrudServlet.SORT);
		if (sortBucketBy != null) {
			essb.setSortOrder(sortBucketBy);
		}
		
		String sortExampleBy = state.get(SORT_EXAMPLE);
		if (sortExampleBy != null) {
			essb.setSortExample(sortExampleBy);
		}
		
		
		// shard preference
		// See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-shard-routing.html
		boolean localshard = state.get(LOCALSHARD);
		if (localshard) {					// Explicit local shard
			essb.setPreference("_local");
		} else if (fixedRandomSeed) {		// If we need predictable random sampling we need to fix shard order
			essb.setPreference("Good-Loop");	// value doesn't matter so long as always the same!
			// TODO use some user/session based value instead to spread load????
		}
		
		// Flags that change the result so must be cached separately		
		boolean isDebug = state.debug && isLoggedIn(state);

		// Use the cache?
		boolean nocache = state.get(CommonFields.NOCACHE);
		
		// Cache key includes search request, user auth, debug, and sampling settings if applic.
		String cachekey = XStreamUtils.serialiseToXml(essb) + isGLUser + isDebug + probability + sigFig;
		
		if (!nocache) {
			Map agg = cacheAggregations.get(cachekey);
			if (agg != null) {
				if (state.getResponseType() == KResponseType.csv) {
					process2_sendCSV(state, agg);
				} else {
					agg.put("cache", true); // debug: let the user know
//					agg.put("cacheKey", cachekey); // debug
					JsonResponse jr = new JsonResponse(state, agg);
					WebUtils2.sendJson(jr, state);
				}
				return;
			}
		}

		SearchRequest search = essb.prepareSearch();
		search.setDebug(isDebug);		

		// Search!
		SearchResponse sr = search.get();
		sr.check();

		Map<String, Object> aggregations = sr.getAggregations();
		if (aggregations == null) {
			Log.d(LOGTAG, "No aggregations?! " + state + " " + sr);
			aggregations = new ArrayMap();
		}
		// strip out no0 filter wrappers
		aggregations = essb.cleanJson(aggregations);
		
		if (sigFig != null) {
			aggregations = essb.roundingSigFig(aggregations, sigFig);
		}

		
		if (isGLUser) {		// Examples / raw data restricted to GL users
			// also send eg data
			aggregations.put("examples", sr.getHits());		
			// also send paging data
			if (paging != null) {
				String pit = sr.getPointInTime();
				List sa = sr.getSearchAfter();
				aggregations.put("paging", new ArrayMap(
					"pit",pit,
					"search_after", sa
				));
			}
		}
		
		// debug?
		if (isDebug) {
			aggregations.put("debug", search.getCurl());
			aggregations.put("took", sr.getTook());
		}
		
		// also for debug
		aggregations.put("start", start.toISOString());
		aggregations.put("end", end.toISOString());
		aggregations.put("hits_total", sr.getTotal());
		
		// done		
		// send CSV or JSON?
		if (state.getResponseType()==KResponseType.csv) {
			process2_sendCSV(state, aggregations);
		} else {
			JsonResponse jr = new JsonResponse(state, aggregations);		
			WebUtils2.sendJson(jr, state);
		}

		// add to cache
		if (!nocache) cacheAggregations.put(cachekey, aggregations);
	}
	
	/**
	 * Nasty bodge to make use of the GL user checker in CrudServlet.
	 */
	class GLUserChecker extends CrudServlet<Void>
	{
		public GLUserChecker() {
			super(Void.class, new StdESRouter());
		}
		
		/**
		 * GL and isDev
		 * @param state
		 * @return
		 */
		boolean check(WebRequest state) {
			return isGLSecurityHack(state) && WinterwellProjectFinder.isDev(state);
		}
		
	}
	GLUserChecker glUserChecker = new GLUserChecker();

	private void process2_sendCSV(WebRequest state, Map<String, Object> aggregations) {
		Map agg = null;
		String key = null;
		for(String k : aggregations.keySet()) {
			if (k.startsWith("by_")) {
				key = k;
				agg = (Map) aggregations.get(k);
			}
		}
		doSendCSV(state, key, agg);
	}


	/**
	 * @param state
	 * @return true if state has an AuthToken. Does NOT check validity of the token!
	 */
	boolean isLoggedIn(WebRequest state) {		
		YouAgainClient yac = Dep.get(YouAgainClient.class);
		List<AuthToken> authd = yac.getAuthTokens(state);
		for(AuthToken at : authd) {
			if (at.isTemp()) continue;
			return true;
		}
		return false;
	}

	/**
	 * Convert json into a csv (fairly crudely)
	 * @param state
	 * @param agg
	 * @param agg2 
	 */
	private void doSendCSV(WebRequest state, String key, Map agg) {
		HttpServletResponse response = state.getResponse();		
		BufferedWriter out = null;
		try {
			response.setContentType(WebUtils.MIME_TYPE_CSV);
			out = FileUtils.getWriter(response.getOutputStream());
			CSVWriter w = new CSVWriter(out, new CSVSpec());

			// send a two column csv
			doSendCSV2(state, key, agg, w);

			// egs??
//			Json2Csv j2c = new Json2Csv(w);
//
//			// optionally have headers set
//			List<String> headers = state.get(new ListField<>("headers"));
//			if (headers != null) {
//				j2c.setHeaders(headers);
//			}
//			// convert!
//			j2c.run(agg);

			FileUtils.close(w);
		} catch (IOException e) {
			throw Utils.runtime(e);
		} finally {
			FileUtils.close(out);
		}
	}

	/**
	 * Send a two column csv: key, count
	 * @param state
	 * @param key e.g. "by_cid"
	 * @param agg
	 * @param w
	 */
	private void doSendCSV2(WebRequest state, String key, Map agg, CSVWriter w) {
		w.write(key.substring(3), "count");
		List<Map<String,Object>> buckets = SimpleJson.getList(agg, "buckets");
		for (Map<String, Object> map : buckets) {
			Object k = map.get("key");
			Object cnt = map.get("count");
			w.write(k, cnt);
		}
	}


}

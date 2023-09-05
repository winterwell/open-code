package com.winterwell.datalog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import com.winterwell.datalog.DataLog.KInterpolate;
import com.winterwell.es.ESPath;
import com.winterwell.es.client.BulkRequest;
import com.winterwell.es.client.ESConfig;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.ESHttpResponse;
import com.winterwell.es.client.IndexRequest;
import com.winterwell.es.client.PainlessScriptBuilder;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.es.client.UpdateRequest;
import com.winterwell.es.client.agg.Aggregations;
import com.winterwell.es.client.query.ESQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilders;
import com.winterwell.es.client.sort.KSortOrder;
import com.winterwell.es.client.sort.Sort;
import com.winterwell.gson.Gson;
import com.winterwell.maths.stats.distributions.d1.IDistribution1D;
import com.winterwell.maths.timeseries.Datum;
import com.winterwell.maths.timeseries.IDataStream;
import com.winterwell.maths.timeseries.ListDataStream;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.ArraySet;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.ListMap;
import com.winterwell.utils.containers.Pair2;
import com.winterwell.utils.io.ConfigBuilder;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.log.WeirdException;
import com.winterwell.utils.threads.IFuture;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.XStreamUtils;

/**
 * ElasticSearch backed storage for DataLog
 * 
 * @testedby  ESStorageTest}
 * @author daniel
 *
 */
public class ESStorage implements IDataLogStorage {

	public static final String count = "count";
	private static final String LOGTAG = "DataLog.ES";
	private ESConfig esConfig;
	
	@Override
	public void save(Period period, Map<String, Double> tag2count, Map<String, IDistribution1D> tag2mean) {
		Collection<DataLogEvent> events = new ArrayList();
		for(Entry<String, Double> tc : tag2count.entrySet()) {
			DataLogEvent event = event4tag(tc.getKey(), tc.getValue());
			events.add(event);
		}
		for(Entry<String, IDistribution1D> tm : tag2mean.entrySet()) {
			DataLogEvent event = event4distro(tm.getKey(), tm.getValue());
			events.add(event);
		}
		saveEvents(events, period);
	}

	/**
	 * make an event to store a distribution stat
	 * @param tm
	 * @return
	 */
	DataLogEvent event4distro(String stag, IDistribution1D distro) {
		DataLogEvent event = event4tag(stag, distro.getMean());
		// stash the whole thing, pref using json (but encoded as a string, so that ES won't go wild on index fields)
		if (Dep.has(Gson.class)) {
			Gson gson = Dep.get(Gson.class);
			Object json = gson.toJson(distro);
			ArrayMap xtra = new ArrayMap("gson", json);				
			event.setExtraResults(xtra);
		} else {
			ArrayMap xtra = new ArrayMap("xml", XStreamUtils.serialiseToXml(distro));				
			event.setExtraResults(xtra);
		}
		return event;
	}

	@Override
	public void saveHistory(Map<Pair2<String, Time>, Double> tag2time2count) {		
		for(Entry<Pair2<String, Time>, Double> tc : tag2time2count.entrySet()) {
			DataLogEvent event = event4tag(tc.getKey().first, tc.getValue());
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

	@Override
	public StatReq<IDataStream> getData(String tag, Time start, Time end, KInterpolate fn, Dt bucketSize) {
		DataLogEvent spec = eventspec4tag(tag);
		SearchResponse sr = getData2(spec, start, end, true);
		List<Map<String, Object>> hits = sr.getSearchResults();
		ListDataStream list = new ListDataStream(1);
		for (Map hit : hits) {
			Object t = hit.get("time");
			Time time = Time.of(t.toString());
			Number vcount = (Number) hit.get(count);
			Datum d = new Datum(time, vcount.doubleValue(), tag);
			list.add(d);
		}
		// TODO interpolate and buckets
		return new StatReqFixed<IDataStream>(list);
	}

	@Override
	public StatReq<Double> getTotal(String tag, Time start, Time end) {
		DataLogEvent spec = eventspec4tag(tag);
		double total = getEventTotal(start, end, spec);
		return new StatReqFixed<Double>(total);
	}

	private DataLogEvent eventspec4tag(String tag) {
		return event4tag(tag, 0);
	}
	
	@Deprecated // use new DataLogEVent directly
	static DataLogEvent event4tag(String tag, double _count) {
		return new DataLogEvent(tag, _count);
	}

	@Override
	public Iterator getReader(String server, Time start, Time end, Pattern tagMatcher, String tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFuture<MeanRate> getMean(Time start, Time end, String tag) {
		// TODO aggregate down in ES?
		StatReq<IDataStream> mdata = getMeanData(tag, start, end, KInterpolate.SKIP_ZEROS, null);
		// TODO Auto-generated method stub
		throw new TodoException();
	}

	@Override
	public StatReq<IDataStream> getMeanData(String tag, Time start, Time end, KInterpolate fn, Dt bucketSize) {
		DataLogEvent spec = eventspec4tag(tag);
		SearchResponse sr = getData2(spec, start, end, true);
		List<Map<String, Object>> hits = sr.getSearchResults();
		ListDataStream list = new ListDataStream(1);
		for (Map hit : hits) {
			Object t = hit.get("time");
			Object xtra = hit.get("xtra");
			Time time = Time.of(t.toString());
			Number vcount = (Number) hit.get(count);
			Datum d = new Datum(time, vcount.doubleValue(), tag);
			list.add(d);
		}
		// TODO interpolate and buckets
		return new StatReqFixed<IDataStream>(list);
	}

	@Override
	public void setHistory(Map<Pair2<String, Time>, Double> tagTime2set) {
		// TODO Auto-generated method stub
	}

	public ESStorage init(DataLogConfig config) {
//		this.config = config; only used here
		// ES config -- which might not match the ESConfig used by the rest of the app! 
		// (??is that ever wanted??)
		if (esConfig == null) {			
			ConfigFactory cf = ConfigFactory.get();
			ConfigBuilder cb = cf.getConfigBuilder(ESConfig.class);
			// also look in config/datalog.properties (as well as es.properties)
			esConfig = cb.set(new File("config/datalog.properties")).get();			
			// check the connection
			ESHttpClient es = new ESHttpClient(esConfig);
			es.checkConnection();
		}
		// Support per-namespace ESConfigs
		if (config.namespaceConfigs!=null) {
			synchronized (config4dataspace) {
				for (String n : config.namespaceConfigs) {
					// also look in config/datalog.namespace.properties
					File f = new File("config/datalog."+n.toLowerCase()+".properties");
					Log.d(LOGTAG, "Looking for special namespace "+n+" config in "+f+" file-exists: "+f.exists());
					if ( ! f.exists()) {
						Log.w(LOGTAG, "No special config file "+f.getAbsoluteFile());
						continue;
					}
					ConfigFactory cf = ConfigFactory.get();
					ConfigBuilder cb = cf.getConfigBuilder(ESConfig.class);
					ESConfig esConfig4n = cb
							.set(new File("config/datalog.properties"))
							.set(f)
							.get();
					Dataspace ds = new Dataspace(n);
					config4dataspace.put(ds, esConfig4n);					
				}
			}
		}
		// init
		ArraySet<Dataspace> dataspaces = new ArraySet();
		if ( ! Utils.isBlank(config.namespace)) {
			dataspaces.add(new Dataspace(config.namespace));
		}
		if (config.namespaceConfigs!=null) {
			dataspaces.addAll(Containers.apply(config.namespaceConfigs, Dataspace::new));
		}
		for (Dataspace d : dataspaces) {			
			registerDataspace(d);
		}
		// share via Dep
		Dep.setIfAbsent(ESStorage.class, this);
		return this;
	}

	/**
	 * 
	 * @param dataspace
	 * @return an alias which should always point to one base-index
	 */
	public static String writeIndexFromDataspace(Dataspace dataspace) {
		assert ! Utils.isBlank(dataspace);
		assert ! dataspace.name.startsWith("datalog.") : dataspace;
		String idx = "datalog."+dataspace;
		return idx;
	}
	
	/**
	 * 
	 * @param dataspace
	 * @return an alias which should point to all indices
	 */
	public static String readIndexFromDataspace(Dataspace dataspace) {
		assert ! Utils.isBlank(dataspace);
		assert ! dataspace.name.startsWith("datalog.") : dataspace;
		String idx = "datalog."+dataspace+".all";
		return idx;
	}

	/**
	 * Call {@link #init(DataLogConfig)} before use
	 */
	public ESStorage() {
	}
	
	/**
	 * WARNING TODO: historical events (which we don't really use) will get saved into the current base index.
	 * That's probably not what you want. Although it's currently completely harmless.
	 * 
	 * @param cnt
	 * @param dataspace
	 * @param event
	 * @param bucketPeriod 
	 * @return 
	 */
	@Override
	public Future<ESHttpResponse> saveEvent(Dataspace dataspace, DataLogEvent event, Period bucketPeriod) {
		Log.d(LOGTAG, "saveEvent "+event);
		if (event.dataspace!=null && ! event.dataspace.equals(dataspace.name)) {
			Log.e(LOGTAG, new WeirdException("(swallowing) Dataspace mismatch: "+dataspace+" vs "+event.dataspace+" in "+event));
		}
		// init?
		registerDataspace(dataspace);
		
		// ID
		String id;
		boolean grpById = event.groupById!=null; 
		if (grpById) {
			// HACK group by means no time bucketing
			id = event.getId();
		} else {
			// put a time marker on it -- the end in seconds is enough
			long secs = event.getTime().getTime() / 1000;
			id = event.getId()+"_"+secs;
		}
		
		// always have a time
		if (event.time==null) {
			event.time = bucketPeriod.getEnd();
		}
		
		ESHttpClient client = client(dataspace);		
		
		String index = writeIndexFromDataspace(dataspace);
		Log.d(LOGTAG, "saveEvent to index "+index+" "+event);
		esdim.prepWriteIndex(client, dataspace, bucketPeriod.getEnd());
		// save -- update for grouped events, index otherwise
		ESPath path = new ESPath(index, id);
		Future<ESHttpResponse> f;
		if (grpById && !event.overwrite) {
			UpdateRequest saveReq = client.prepareUpdate(path);
//			saveReq.setDebug(true); // Debugging Sep 2018 (this will be noisy)
			// try x3 before failing
			saveReq.setRetries(2);
			// set doc
			Map<String, Object> doc = event.toJson2();
			PainlessScriptBuilder psb = PainlessScriptBuilder.fromJsonObject(doc);
			saveReq.setScript(psb);
			// upsert		
			saveReq.setUpsert(doc);
			f = saveReq.execute();
		} else {
			IndexRequest saveReq = client.prepareIndex(path);
//			saveReq.setDebug(true); // Debugging Dec 2021 (this will be noisy)
			// set doc
			Map<String, Object> doc = event.toJson2();			
			saveReq.setBodyMap(doc);
			f = saveReq.execute();
		}		
		client.close();
		
		// commented out - paranoia dec 2021 - something is leaking ESHttpClient objects
//		// log stuff ??does this create a resource leak??
//		if (f instanceof ListenableFuture) {
//			((ListenableFuture<ESHttpResponse>) f).addListener(() -> {			
//				try {
//					ESHttpResponse response = f.get();
//					response.check();
//				} catch(Throwable ex) {
//					Log.e(DataLog.LOGTAG, "...saveEvent FAIL :( "+ex+" from event: "+event);
//				}
//			}, MoreExecutors.directExecutor());
//		}
		
		return f;
	}
	
	@Override
	public void flush() {
		// wait a second
		Utils.sleep(1000);
	}

	@Override
	public String toString() {
		return "ESStorage [esConfig=" + esConfig + "]";
	}


	/**
	 * All get stored as one type in ES 'cos multiple types is being deprecated as "kind of broken"
	 */
	static final String ESTYPE = "evt";

//	@Override
//	public void registerEventType(Dataspace dataspace, String eventType) {		
//		ESHttpClient _client = client(dataspace);
//		registerEventType2(_client, dataspace, new Time());
//	}
	
	public double getEventTotal(Time start, Time end, DataLogEvent spec) {
		SearchResponse sr = getData2(spec, start, end, false);
		Map<String, Object> jobj = sr.getParsedJson();
		List<Map> hits = sr.getHits();
		Map aggs = sr.getAggregations();
		Map stats = (Map) aggs.get("event_total");
		Object sum = stats.get("sum");
		double total = MathUtils.toNum(sum);
		// Add in the last bucket
		// If you request data up to the present moment, then the last data-point is not in the database -- so we add it from memory.
		DataLogImpl impl = (DataLogImpl) DataLog.getImplementation();
		String tag = DataLogImpl.event2tag(spec.dataspace, spec.toJson2());
		Datum latest = impl.currentBucket(tag, end);
		// TODO
//		List<Datum> curHistData = stat.currentHistoric(tag, start, end);
		if (latest!=null) total += latest.x();
		return total;
	}
	
	SearchResponse getData2(DataLogEvent spec, Time start, Time end, boolean sortByTime) {
		// hm - ??possibly we should extend time to the end of the current bucket
		// -- but that only affects the corner case of flush().
//		if end
//		DataLogImpl dl = (DataLogImpl) DataLog.getImplementation();
//		Period bucketPeriod = dl.getBucket(event.time);
//		if end in bucket, end at end of bucket
		
		DataLogConfig config = Dep.get(DataLogConfig.class);		
		final Dataspace dataspace = new Dataspace(spec.dataspace);
		String index = readIndexFromDataspace(dataspace);
		SearchRequest search = client(dataspace).prepareSearch(index);
		search.setType(ESTYPE);
		search.setSize(config.maxDataPoints);
		
		com.winterwell.es.client.query.BoolQueryBuilder filter = ESQueryBuilders.boolQuery();
//		BoolQueryBuilder filter = QueryBuilders.boolQuery();
				
		// time box?
		if (start !=null || end != null) {
			ESQueryBuilder timeFilter = ESQueryBuilders.dateRangeQuery("time", start, end);			
			filter = filter.filter(timeFilter);
		}
		
		// event tag match
		String[] evts = spec.getEventType();
		for (String evt : evts) {			
			ESQueryBuilder tagFilter = ESQueryBuilders.termQuery(DataLogEvent.EVT, evt);
			filter = filter.filter(tagFilter);
		}
		// any props requested?
		for(Entry<String, Object> prop : spec.getProps().entrySet()) {
			Class pType = DataLogEvent.COMMON_PROPS.get(prop.getKey());
			if (pType==null) {
				throw new TodoException("filter by "+prop+" "+spec);
			}
			ESQueryBuilder propFilter = ESQueryBuilders.termQuery(prop.getKey(), prop.getValue());
			filter = filter.filter(propFilter);
		}
		
		search.setQuery(filter);
		
		if (sortByTime) {
			Sort sort = new Sort("time", KSortOrder.asc);
			search.addSort(sort);
		}

		// stats or just sum??
		if (sortByTime) {
			
		} else {
			search.addAggregation(Aggregations.stats("event_total", count));
			search.setSize(0);
		}
//		ListenableFuture<ESHttpResponse> sf = search.execute(); TODO return a future
//		client.debug = true;
		SearchResponse sr = search.get();
//		client.debug = false;
		return sr;
	}

	static Map<Dataspace, ESConfig> config4dataspace = new HashMap();
	
	/**
	 * ??makes a new object each time - why??
	 * @param dataspace Can be null
	 * @return
	 */
	public ESHttpClient client(Dataspace dataspace) {		
		ESConfig _config = Utils.or(config4dataspace.get(dataspace), esConfig);
		assert _config != null : dataspace+" "+esConfig;
		ESHttpClient esjc = new ESHttpClient(_config);
		esjc.debug = true; // slow datalog DEBUG Dec 2021 - please remove when fixed
		return esjc;
	}

	/**
	 * @param bucketPeriod Can be null (will be taken from the events)
	 * see https://www.elastic.co/guide/en/elasticsearch/reference/current/tune-for-indexing-speed.html
	 */
	@Override
	public void saveEvents(Collection<DataLogEvent> events, Period bucketPeriod) {
		// use a batch-save for speed
		Log.d(LOGTAG, "saveEvents "+events.size());
		ListMap<String,DataLogEvent> events4dataspace = new ListMap();
		for (DataLogEvent dle : events) {
			events4dataspace.add(dle.dataspace, dle);
		}
		for(String d : events4dataspace.keySet()) {
			saveEvents2(d, events4dataspace.get(d), bucketPeriod);
		}
	}
	
	/**
	 * WARNING: writes to the X_monthyear indices. If you've manually renamed an index it writes to - that could go wrong!
	 * 
	 * TODO refactor to be used by saveEvent as well
	 * 
	 * @param dataspace
	 * @param events
	 * @param bucketPeriod Can be null (will be taken from the events)
	 */
	void saveEvents2(String dataspace, List<DataLogEvent> events, Period bucketPeriod) {		
		// init?
		Dataspace d = new Dataspace(dataspace);
		registerDataspace(d);
		ESHttpClient client = client(d);
		
		BulkRequest bulk = client.prepareBulk();
		int batchSize = 500; // TODO tune this
		DataLogImpl dl = (DataLogImpl) DataLog.getImplementation();
		for (DataLogEvent event : events) {
			// which bucket?
			Time bucketEnd;
			if (bucketPeriod==null) {
				if (event.time==null) {
					throw new NullPointerException("No groupById or bucketPeriod or event.time: "+event);
				}
				bucketEnd = dl.getBucket(event.time).getEnd();
			} else {
				bucketEnd = bucketPeriod.getEnd();
			}
			// ID
			String id;
			boolean grpById = ! Utils.isBlank(event.groupById); 
			if (grpById) {
				// HACK group by means no time bucketing
				id = event.groupById; // NB: this is also what event.getId() returns when groupById is set
			} else {				
				// put a time marker on it -- the end in seconds is enough
				long secs = bucketEnd.getTime() % 1000;
				id = event.getId()+"_"+secs;
			}
			
			// always have a time
			if (event.time==null) {
				event.time = bucketEnd;
			}					
		
			// save -- update for grouped events, index otherwise
			String baseIndex = ESDataLogIndexManager.baseIndexFromDataspace(d, bucketEnd);
			esdim.registerDataspace2(d, baseIndex);
//			esdim.prepWriteIndex(client, d, bucketEnd); not needed - we write direct to baseIndex			
			ESPath path = new ESPath(baseIndex, id);			
			if (grpById && !event.overwrite) {
				UpdateRequest saveReq = client.prepareUpdate(path);
	//			saveReq.setDebug(true); // Debugging Sep 2018 (this will be noisy)
				// try x3 before failing
				saveReq.setRetries(2);
				// set doc
				Map<String, Object> doc = event.toJson2();
				PainlessScriptBuilder psb = PainlessScriptBuilder.fromJsonObject(doc);
				saveReq.setScript(psb);
				// upsert		
				saveReq.setUpsert(doc);
				bulk.add(saveReq);
			} else {
				IndexRequest saveReq = client.prepareIndex(path);
				// set doc
				Map<String, Object> doc = event.toJson2();			
				saveReq.setBodyMap(doc);
				bulk.add(saveReq);
			}					
			if (bulk.size()==batchSize) {
				bulk.execute();
				bulk = client.prepareBulk();
			}			
		}		
		if (bulk.size()!=0) {
			bulk.execute();
		}
		client.close();
	}
	
	
	ESDataLogIndexManager esdim = new ESDataLogIndexManager(this);

	public ESDataLogIndexManager getESDataLogIndexManager() {
		return esdim;
	}
	
	/**
	 * Repeated calls are fast and harmless
	 * @param dataspace
	 */
	public boolean registerDataspace(Dataspace dataspace) {
		return esdim.registerDataspace(dataspace);
	}
	
	/**
	 * Convenience for making and calling {@link ESDataLogSearchBuilder}
	 * 
	 * @param dataspace
	 * @param numResults
	 * @param numExamples
	 * @param start
	 * @param end
	 * @param query
	 * @param breakdown
	 * @return
	 */
	public SearchResponse doSearchEvents(Dataspace dataspace, 
			int numResults, int numExamples, 
			Time start, Time end, 
			SearchQuery query, List<String> breakdown) 
	{
		ESHttpClient esc = client(dataspace);
		
		ESDataLogSearchBuilder essb = new ESDataLogSearchBuilder(esc, dataspace);		
		essb.setBreakdown(breakdown)
			.setQuery(query)
			.setNumResults(numResults)
			.setStart(start)
			.setEnd(end);
		
		SearchRequest search = essb.prepareSearch();		
		search.setDebug(true);
//		search.setType(typeFromEventType(spec.eventType)); all types unless fixed
		// size controls
		search.setSize(numExamples);
		
		SearchResponse sr = search.get();
		return sr;
	}
	
}

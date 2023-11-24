package com.winterwell.datalog;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.es.client.agg.Aggregation;
import com.winterwell.es.client.agg.AggregationResults;
import com.winterwell.es.client.agg.Aggregations;
import com.winterwell.es.client.query.BoolQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilders;
import com.winterwell.es.client.sort.KSortOrder;
import com.winterwell.es.client.sort.Sort;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.VersionString;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.WebEx;
import com.winterwell.web.app.AppUtils;
import com.winterwell.web.app.WebRequest;

/**
 * Special Fields
 * 
 *  "time" => Aggregations.dateHistogram() using interval
	"dateRange" => Aggregations.dateRange();
	"timeofday" => turn time into hour of the day
 * 
 * @testedby  ESDataLogSearchBuilderTest
 * @author daniel
 *
 */
public class ESDataLogSearchBuilder {
	
	public static final String EMISSIONS = "emissions";
	public static final String COUNTCO2 = "countco2";
	
	/**
	 * magic marker for "bucket by the hour"
	 */
	public static final String TIMEOFDAY = "timeofday";
	
	private static final String no0_ = "no0_";
	/**
	 * maximum number of ops you can request in one breakdown.
	 * Normally just 1!
	 */
	private static final int MAX_OPS = 10;
	private static final String LOGTAG = "ESDataLogSearchBuilder";
	private static int sumOtherDocCount;
	final Dataspace dataspace;
	int numResults;
	int numExamples; 
	Time start;
	Time end; 
	SearchQuery query;
	List<String> breakdown;
	String bucketTimeZone;
	private boolean doneFlag;
	private ESHttpClient esc;
	private Map<String,Map> runtimeMappings;

	/**
	 * For point-int-time PIT searches - the max gap between requests
	 */
	private Dt keep_alive = new Dt(5, TUnit.MINUTE);
	private boolean incStart = true;
	/**
	 * TODO we'd like to switch the default to false, but are wary of creating bugs due to prev behaviour
	 * NOTE But we do set default:false in DataServlet!
	 */
	private boolean incEnd = true;

	private static VersionString _esVersion;
	
	/**
	 * Were there missed documents?
	 * @return hopefully 0
	 */
	public static int getSumOtherDocCount() {
		return sumOtherDocCount;
	}
	
	public ESDataLogSearchBuilder(ESHttpClient esc, Dataspace dataspace) {
		this.dataspace = dataspace;
		this.esc = esc;
	}
	
	public ESDataLogSearchBuilder setBreakdown(List<String> breakdown) {
		assert ! doneFlag;
		this.breakdown = breakdown;
		return this;
	}
	
	/**
	 * NB: This will be converted using {@link AppUtils#makeESFilterFromSearchQuery(SearchQuery, Time, Time)}
	 * @param query
	 * @return
	 */
	public ESDataLogSearchBuilder setQuery(SearchQuery query) {
		assert ! doneFlag;
		this.query = query;
		return this;
	}
	
	public SearchRequest prepareSearch() {
		doneFlag = true;
		
		SearchRequest search = prepareSearch2_filter();

		Double prob = randomSamplingProb; 
		// Use prob -1 to activate smart sampling
		if (randomSamplingProb != null && randomSamplingProb < 0) {
			prob = prepareSearch2_whatSampling();
		}
		
		// breakdown(s)
		List<Aggregation> aggs = prepareSearch2_aggregations();
		// prob=1 => no sampling
		if (prob!=null && prob!=0) { // Hack guard against 0 ??should we throw an error instead?
			assert MathUtils.isProb(prob);
			// NB: ES will object if prob is >0.5 <1
			Aggregation raggs = Aggregations.random("sampling", prob, aggs);
			if (randomFixedSeed) {
				raggs.put("seed", 1); // fix the seed = same query will give the same answers
			}
			search.addAggregation(raggs);
		} else {
			for (Aggregation aggregation : aggs) {			
				search.addAggregation(aggregation);
			}
		}
		
		// runtime fields
		if (runtimeMappings!=null) {
			// NB: requires ES version 7.11 and above		
			for(String rf : runtimeMappings.keySet()) {
				search.addRuntimeMapping(rf, runtimeMappings.get(rf));
			}
		}
		
		if (sortExampleBy != null) {
			KSortOrder kSortOrder = "desc".equals(sortExampleBy) ? KSortOrder.desc : KSortOrder.asc;
			search.setSort(new Sort("time", kSortOrder));
		}
		
		// paging?
		if (paging!=null) {
			// https://www.elastic.co/guide/en/elasticsearch/reference/8.3/paginate-search-results.html
//			https://www.elastic.co/guide/en/elasticsearch/reference/8.3/point-in-time-api.html
			// efficiency sort
			search.setSort(Sort._SHARD_DOC);
			search.setTrackTotalHits(false);
			// get a PIT
			String pit = SimpleJson.get(paging, "pit");
			if (pit==null) {
				String index = ESStorage.readIndexFromDataspace(dataspace);
				pit = esc.getPointInTime(index, keep_alive);
			}
			search.setPointInTime(pit, keep_alive);
			List sa = SimpleJson.getList(paging, "search_after");
			if (sa != null) {
				search.setSearchAfter(sa);					
			}
		}
		
		if (numExamples != 0) {			
			search.setSize(numExamples);
		}
		
		if (preference != null) {
			search.setPreference(preference);
		}

		return search;
	}
	
	private SearchRequest prepareSearch2_filter() {	
		String index = ESStorage.readIndexFromDataspace(dataspace);
		
		SearchRequest search = esc.prepareSearch(index);
		
		// Set filter
		if (query == null) {
			Log.w(LOGTAG, "no query? "+this);
		} else {
			Collection<String> restrictTextSearchFields = null;
			BoolQueryBuilder filter = AppUtils.makeESFilterFromSearchQuery(query, start, incStart, end, incEnd, restrictTextSearchFields);
			search.setQuery(filter);
		}
		
		return search;
	}

	/**
	 * Do a pre-search to get a doc count. Then pick a sampling level.
	 * @return [0,1] lower end is capped at 0.1
	 */
	private Double prepareSearch2_whatSampling() {
		assert randomSamplingProb < 0 : this;
		// Random sampling algorithm
		SearchRequest preSearch = prepareSearch2_filter();

		// cardinality? nope - no unique field we can count, since _id is not allowed
		// count with sampling
		Aggregation countAgg = Aggregations.count("n", "count");
		Aggregation fCountStats = Aggregations.random("sampling", 0.01, countAgg);
		preSearch.addAggregation(fCountStats);
		
		preSearch.setSize(0);

		SearchResponse preSr = preSearch.get();
		Long total = preSr.getTotal();
		AggregationResults agg = preSr.getAggregationResults("sampling");
		Number totalDocs = SimpleJson.get(agg.map(), "n", "value");
		Log.d(LOGTAG, "whatSampling total: "+total+" count: "+totalDocs);
		long n = totalDocs.longValue();
//		Map<String, Double> allCount = (Map<String, Double>) preSr.getAggregationResults("cardinality")
//				.getField("allCount");
//		double total = allCount.get("value");
		
		long threshold = esc.getConfig().randomSampleThreshold;
		
		if (threshold > n) {
			return 1.0;
		}
		double probability = Math.max((double) threshold / n, 0.1);
		probability = Math.min(probability, 0.5);
		return probability;
	}


	/**
	 * Number of buckets in aggregations -- see {@link Aggregation#setSize(int)}
	 * @param numResults
	 * @return
	 */
	public ESDataLogSearchBuilder setNumResults(int numResults) {
		assert ! doneFlag;
		this.numResults = numResults;
		return this;
	}
	
	
	

	/**
	 * Add aggregations 
	 */
	List<Aggregation> prepareSearch2_aggregations() 
	{
		List<Aggregation> aggs = new ArrayList();
		for(final String bd : breakdown) {
			if (Utils.isBlank(bd)) {				
				continue;
			}
			Aggregation agg = prepareSearch3_agg4breakdown(bd);
			aggs.add(agg);
		} // ./breakdown
		
		// add a total count as well for each top-level terms breakdown
		Aggregation fCountStats = Aggregations.sum(allCount, ESStorage.count);
		aggs.add(fCountStats);			
		
		return aggs;
	}
	
	public static final String allCount = "allCount";

	/**
	 * Contains a special hack for "timeofday" which uses a synthetic field
	 * 
	 * @param bd Format: bucket-by-fields/ {"report-fields": "operation"} 
	 * 	e.g. "evt" or "evt/time" or "tag/time {"mycount":"avg"}"
	 * NB: the latter part is optional, but if present must be valid json.
	 *   
	 * @return
	 */
	private Aggregation prepareSearch3_agg4breakdown(String bd) {		
		// TODO refactor to use Breakdown
		Breakdown _breakdown = Breakdown.fromString(bd);
		// ??Is there a use-case for recursive handling??
		String[] breakdown_output = bd.split("\\{");
		String[] bucketBy = breakdown_output[0].trim().split("/");
		Map<String,String> reportSpec = new ArrayMap("count","sum"); // default to sum of `count`
		if (breakdown_output.length > 1) {
			String json = bd.substring(bd.indexOf("{"), bd.length());
			if (("{\""+EMISSIONS+"\":\"sum\"}").equals(json)) {
				// Hack: shortcut for greendata emissions
				json = "{\"count\":\"sum\",\"co2\":\"sum\",\"co2base\":\"sum\",\"co2creative\":\"sum\",\"co2supplypath\":\"sum\"}";
			} else if (("{\""+COUNTCO2+"\":\"sum\"}").equals(json)) {
				json = "{\"count\":\"sum\",\"co2\":\"sum\"}"; // Hack: emissions shortcut for performance
			}
			try {
				reportSpec = WebUtils2.parseJSON(json);
			} catch(Exception pex) {
				throw new WebEx.BadParameterException("breakdown", "invalid json: "+json, pex);
			}
		}
		// loop over the f1/f2 part, building a chain of nested aggregations
		Aggregation root = null;
		Aggregation leaf = null;
		Aggregation previousLeaf = null;
		String s_bucketBy = StrUtils.join(bucketBy, '_');
		
		for(String field : bucketBy) {
			if (Utils.isBlank(field)) {
				// "" -- use-case: you get this with top-level "sum all"
				continue;
			}
			if (field.equals("time")) {
				leaf = Aggregations.dateHistogram("by_"+s_bucketBy, "time", interval, bucketTimeZone);
			} else if (field.equals("dateRange")) {
				// TODO test vs fencepost issues
				// A slightly hacky option. Use-case: return stats for the 
				// 	last week, the week before (to allow "+25%" comparisons), and older
				Time now = end;
				Time prev = now.minus(interval);
				Time prev2 = prev.minus(interval);
				List<Time> times = Arrays.asList(start, prev2, prev, now);
				leaf = Aggregations.dateRange("by_"+s_bucketBy, "time", times);
			} else if (field.equals(TIMEOFDAY)) {
				// HACK turn time into hour of the day
				// see https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html#date-histogram-aggregate-scripts				
				// what ES vesrsion are we talking to??
				if (esVersion().geq("7.11")) {
					String runtimeField = "time.timeofday";
					if (runtimeMappings==null) runtimeMappings = new ArrayMap();
					runtimeMappings.put(runtimeField, new ArrayMap(
						"type", "long", // NB: integer isn't supported
						"script", "emit(doc['time'].value.hour)"
							));
					leaf = Aggregations.terms("by_"+s_bucketBy, runtimeField);
					leaf.setSize(25);
				} else {
					leaf = Aggregations.termsByScript("by_"+s_bucketBy, "doc['time'].value.hour");
					leaf.setSize(25);
				}
			} else {
				leaf = Aggregations.terms("by_"+s_bucketBy, field);
				if (numResults>0) {
					leaf.setSize(numResults);
				}
				if (sortBy!=null) {
					leaf.setOrder("_key", sortBy);
				}
				// HACK avoid "unset" -> parse exception
				leaf.setMissing(ESQueryBuilders.UNSET);
			}
			if (root==null) {
				root = leaf;
			} else {
				previousLeaf.subAggregation(leaf);
			}
			previousLeaf = leaf;
			// chop down name for the next loop, if there is one.
			if (field.length() < s_bucketBy.length()) {
				s_bucketBy = s_bucketBy.substring(field.length()+1);
			}
		}
		
		// e.g. {"count": "avg"}
		String[] rkeys = reportSpec.keySet().toArray(StrUtils.ARRAY);
		for(int i=0; i<rkeys.length; i++) { // NB: we label part of the aggregations by i
			String k = rkeys[i];
			// safety check: k is a field, not an op
			if (k.equals("sum") || k.equals("avg")) {
				throw new IllegalArgumentException("Bad breakdown {field:op} parameter: "+bd);
			}			
			String op = reportSpec.get(k);
			Aggregation agg;
			// cardinality? histogram? TODO T4G users and tabs-per-user distribution
			if ("cardinality".equals(op)) {
				agg = Aggregations.cardinality(k, k);
			} else if ("histogram".equals(op)) {
				agg = Aggregations.histogram(k, k);
			} else {
				// Note k should be a numeric field, e.g. count -- not a keyword field!
				if ( ! ESStorage.count.equals(k)) {
					Class klass = DataLogEvent.COMMON_PROPS.get(k);
					if ( ! ReflectionUtils.isa(klass, Number.class)) {
						Log.w(LOGTAG, "Possible bug! numeric op on non-numeric field "+k+" in "+bd);
					}
				}
				Aggregation myCount;
				if ("stats".equals(op)) {
					myCount = Aggregations.stats(k,k);
				} else if ("avg".equals(op)) {
					myCount = Aggregations.avg(k,k);					
				} else {
					myCount = Aggregations.sum(k, k);
				}
				// filter 0s??
				ESQueryBuilder no0 = ESQueryBuilders.rangeQuery(k, 0, null, false);
				// NB: we could have multiple ops, so number the keys
				Aggregation noZeroMyCount = Aggregations.filtered(no0_+i, no0, myCount);
				agg = noZeroMyCount;
			}
			
			if (leaf != null) {
				leaf.subAggregation(agg);
				assert root != null;
				continue;
			}
			// this is a top-level sum
			assert root == null;
			leaf = agg;
			root = leaf;			
		}		
		return root;
	}
	

	private VersionString esVersion() {
		if (_esVersion == null) {
			String esv = esc.getESVersion();
			_esVersion = new VersionString(esv);
		}
		return _esVersion;
	}

	public ESDataLogSearchBuilder setStart(Time start) {
		assert ! doneFlag;
		this.start = start;
		return this;
	}

	public ESDataLogSearchBuilder setEnd(Time end) {
		assert ! doneFlag;
		this.end = end;
		return this;
	}
	public ESDataLogSearchBuilder setIncEnd(boolean incEnd) {
		this.incEnd = incEnd;
		return this;
	}
	public ESDataLogSearchBuilder setIncStart(boolean incStart) {
		this.incStart = incStart;
		return this;
	}

	Dt interval = TUnit.DAY.dt;

	private Map paging;

	private String preference;

	private Double randomSamplingProb;
	private boolean randomFixedSeed;
	private String sortBy;
	private String sortExampleBy;
	
	public void setInterval(Dt interval) {
		this.interval = interval;
	}

	/**
	 * Round to a set precision (significant figures)
	 * @param obj
	 * @param mc
	 * @return
	 */
	private Double round(Number obj, MathContext mc) {
		return new BigDecimal(obj.doubleValue()).round(mc).doubleValue();
	}
	
	/**
	 * Round up numbers in the aggregation to significant figures
	 * @param aggregations
	 * @param sigFig
	 * @return aggregations
	 */
	public Map roundingSigFig(Map<String,Object> aggregations, int sigFig) {
		MathContext mc = new MathContext(sigFig);
		BiFunction<Object, List<String>, Object> rnd = (obj, path) -> obj instanceof Number? round((Number)obj, mc) : obj;
		Map<String, Object> rounded = Containers.applyToJsonObject(aggregations, rnd);
		return rounded;
	}


	/**
	 * Remove the no0_ filtering wrappers 'cos they're an annoyance at the client level.
	 * Also remove doc_count for safety.
	 * Simplify count:value:5 to count:5
	 * @param aggregations 
	 * @return cleaned aggregations
	 */
	public Map cleanJson(Map<String,Object> aggregations) {
		Map aggs2 = Containers.applyToJsonObject(aggregations, ESDataLogSearchBuilder::cleanJson2);
		// also top-level
		Map aggs3 = (Map) cleanJson2(aggs2, null);
		return aggs3;
	}	
	
	/**
	 * Visits each node.
	 * NB: also checks for sum_other_doc_count - see https://www.elastic.co/guide/en/elasticsearch/reference/7.10/search-aggregations-bucket-terms-aggregation.html#search-aggregations-bucket-terms-aggregation-approximate-counts		

	 * @param old
	 * @param __path
	 * @return new version of `old`
	 */
	static Object cleanJson2(Object old, List<String> __path) {
		if ( ! (old instanceof Map)) {
			return old;
		}
		Map mold = (Map) old;
		// ?? how to handle missed buckets? At least we can log a warning
		Number sodc = (Number) mold.get("sum_other_doc_count");
		if (Utils.truthy(sodc)) {
			// debug info
			String caller = "";
			WebRequest wr = WebRequest.getCurrent();
			if (wr!=null && wr.isOpen()) caller = wr.getReferer()+" "+wr.getRequestUrl();
			Log.e(LOGTAG, "Uncounted buckets: sum_other_doc_count: "+sodc+" "+__path+" "+caller);
			// track the "other" sum
			sumOtherDocCount += sodc.intValue(); // does our js code use this??
		}
		// no doc_count (its misleading with compression)
		mold.remove("doc_count");
		
		// simplify {value:5} to 5
		if (mold.size() == 1) {
			Object k = Containers.only(mold.keySet());
			if ("value".equals(k)) {
				Number v = (Number) mold.get(k);
				return v;
			}			
		}
		
		Map newMap = null;
		for(int i=0; i<MAX_OPS; i++) {
			Map<String,?> wrapped = (Map) mold.get(no0_+i);
			// no no0s to remove?
			if (wrapped==null) break;
			// copy and edit
			if (newMap==null) newMap = new ArrayMap(mold);
			newMap.remove(no0_+i);
			for(String k : wrapped.keySet()) {
				// Again ignore the doc_count in each wrapped n0_X sub-aggregate
				if (k.equals("doc_count")) continue;
				
				Object v = wrapped.get(k);
				if (v instanceof Map || v instanceof Number) {
					// its some aggregation results :)
					Object oldk = newMap.put(k, v);
					if (oldk!=null) {
						Log.e(LOGTAG, "duplicate aggregation results for "+k+"?! "+newMap);
					}
				}
			}
		}
		Map v = newMap==null? mold : newMap;
		return v;		
	}

	/**
	 * 
	 * @param paging {pit, search_after}
	 * @return
	 */
	public ESDataLogSearchBuilder setPaging(Map paging) {
		this.paging = paging;
		if (numExamples==0) numExamples = 10000; // largest normal page size
		return this;
	}

	/**
	 * @see SearchRequest#setPreference(String)
	 * @param shard
	 */
	public ESDataLogSearchBuilder setPreference(String shard) {
		this.preference = shard;
		return this;
	}
	
	public ESDataLogSearchBuilder setNumExamples(int size) {
		numExamples = size;
		return this;
	}

	/**
	 * @param probability 0 or 1 are both treated as NO random sampling
	 * @param fixedSeed If true, fix the random seed
	 */
	public ESDataLogSearchBuilder setRandomSampling(Double probability, boolean fixedSeed) {
		randomSamplingProb = probability;
		randomFixedSeed = fixedSeed;
		return this;
	}
	
	/**
	 * Optional (defaults to UTC)
	 * @param timezone 
	 */
	public ESDataLogSearchBuilder setBucketTimezone(String timezone) {
		this.bucketTimeZone = timezone;
		return this;
	}

	@Override
	public String toString() {
		return "ESDataLogSearchBuilder [dataspace=" + dataspace + ", query=" + query + ", breakdown=" + breakdown + "]";
	}

	/**
	 * TODO support fieldname-format like here CrudServlet.doList3_addSort()
	 * @param sortBy desc or asc??
	 * @return
	 */
	public ESDataLogSearchBuilder setSortOrder(String sortBy) {
		this.sortBy = sortBy;
		if (sortBy != null && ! "desc".equals(sortBy) && ! "asc".equals(sortBy)) { 
			throw new WebEx.E400("Bad sort - use desc (highest keys), or asc, or null (which gives document_count desc)");
		}
		return this;
	}
	
	/**
	 * TODO change to support fieldname-asc|desc format
	 * @param sortExampleBy currently asc|desc
	 * @return
	 */
	public ESDataLogSearchBuilder setSortExample(String sortExampleBy) {
		this.sortExampleBy = sortExampleBy;
		if (sortBy != null && ! "desc".equals(sortBy) && ! "asc".equals(sortBy)) { 
			throw new WebEx.E400("Bad sort - use desc (latest time), or asc)");
		}
		return this;
	}

	
}

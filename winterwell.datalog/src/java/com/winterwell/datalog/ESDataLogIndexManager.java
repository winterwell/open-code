package com.winterwell.datalog;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.winterwell.datalog.server.CompressDataLogIndexMain;
import com.winterwell.es.ESType;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.IESResponse;
import com.winterwell.es.client.admin.CreateIndexRequest;
import com.winterwell.es.client.admin.CreateIndexRequest.Analyzer;
import com.winterwell.es.client.admin.GetAliasesRequest;
import com.winterwell.es.client.admin.IndicesAdminClient;
import com.winterwell.es.client.admin.IndicesAliasesRequest;
import com.winterwell.es.client.admin.PutMappingRequest;
import com.winterwell.es.fail.ESAlreadyExistsException;
import com.winterwell.utils.Null;
import com.winterwell.utils.io.ConfigBuilder;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;

/**
 * Manage the indices and aliases underneath ESStorage for DataLog
 * 
 * Suppose you have the dataspace "foo"
 * That reads from datalog.foo.all and writes events to datalog.foo -- both of which are aliases. 
 * datalog.foo -> datalog.foo_{month}{year}
 * datalog.foo.all -> datalog.foo.{all the months...}
 * 
 * This class will create the monthly indices and manage aliases.
 * It is invoked automatically by ESStorage.
 * 
 * @see CompressDataLogIndexMain which compresses a month index when the month is over.
 * 
 * @author daniel
 *
 */
public class ESDataLogIndexManager {

	private static final String LOGTAG = "ESDIM";
	private final ESStorage ess;


	/**
	 * Not normally used as a command-line tool but: TODO redo a schema 
	 * @param args
	 */
	public static void main(String[] args) {
		ConfigBuilder cb = ConfigFactory.get().setArgs(args).getConfigBuilder(DataLogConfig.class);
		DataLogConfig config = cb.get();
		ESStorage _ess = new ESStorage();
		_ess.init(config);
		List<String> rem = cb.getRemainderArgs();
		if (rem.isEmpty()) {
			System.err.println(cb.getOptionsMessage("DATASPACE"));
			System.exit(1);
		}
		Dataspace d = new Dataspace(rem.get(0));		
		ESDataLogIndexManager esdim = new ESDataLogIndexManager(_ess);
		esdim.registerDataspace(d);
	}

	public ESDataLogIndexManager(ESStorage esStorage) {
		this.ess = esStorage;
		Log.d(LOGTAG, "new ESDataLogIndexManager");
	}

	/**
	 * Make a base index and set read alias.
	 * 
	 * NB: Write alias is checked and set on-save by {@link #prepWriteIndex(Dataspace)}
	 * @param dataspace
	 * @param now
	 * @return
	 */
	public boolean registerDataspace(Dataspace dataspace) {		
		Time now = new Time();
		String baseIndex = baseIndexFromDataspace(dataspace, now);
		return registerDataspace2(dataspace, baseIndex);
	}
	
	/**
	 * 
	 * @param dataspace Can be null (in which case generic ESConfig is used, and no read index alias is set)
	 * @param baseIndex e.g. "datalog.foo_oct22"
	 * @return true if a new index is setup
	 */
	public boolean registerDataspace2(Dataspace dataspace, String baseIndex) {
		// fast check of cache
		if (knownBaseIndexes.contains(baseIndex)) {
			return false;
		}
		ESHttpClient _client = ess.client(dataspace);
		if (_client.admin().indices().indexExists(baseIndex)) {
			// also check that the index has a mapping! (possible race condition if two saveEvent() calls overlap)
			Map mapping = _client.admin().indices().getMapping(baseIndex);
			if (mapping!=null && mapping.containsKey("count")) {
				knownBaseIndexes.add(baseIndex);
				assert knownBaseIndexes.size() < 100000;			
				return false;
			}
		}
		// make it, with a base and an alias
		return registerDataspace3_doIt(dataspace, baseIndex, _client);
	}
	
	/**
	 * per-month index blocks
	 * @param time
	 * @return the base (i.e. not the alias) index name. e.g. "datalog.foo_oct22"
	 */
	public static String baseIndexFromDataspace(Dataspace dataspace, Time time) {
		// replaces _client.getConfig().getIndexAliasVersion()
		String v = time.format("MMMyy").toLowerCase();
		String index = ESStorage.writeIndexFromDataspace(dataspace);
		return index+"_"+v;
	}
	

	private final static Set<String> knownBaseIndexes = new ConcurrentSkipListSet<>();
	private final static Map<Dataspace,String> activeBase4dataspace = new ConcurrentHashMap();
	

	/**
	 * 
	 * @param dataspace Can be null (then no read alias is set)
	 * @param baseIndex
	 * @param _client
	 * @param now NB: This is to allow testing to simulate time passing
	 * @return
	 */
	private synchronized boolean registerDataspace3_doIt(Dataspace dataspace, String baseIndex, ESHttpClient _client) {		
		try {
			// race condition - check it hasn't been made
			if (_client.admin().indices().indexExists(baseIndex)) {
				Log.i(LOGTAG, "No register dataspace: "+dataspace+" baseIndex: "+baseIndex+" - already exists in ES");							
			} else {
				Log.i(LOGTAG, "register dataspace: "+dataspace+" baseIndex: "+baseIndex);
	
				CreateIndexRequest pc = _client.admin().indices().prepareCreate(baseIndex);			
	//			actually, you can have multiple for all pc.setFailIfAliasExists(true); // this is synchronized, but what about other servers?
				pc.setDefaultAnalyzer(Analyzer.keyword);
				// aliases: index and index.all both point to baseIndex  
				// Set the query index here. The write one is set later as an atomic swap.			
				if (dataspace==null) {
					Log.d(LOGTAG, "null dataspace. No readIndex alias set for "+baseIndex);
				} else {
					pc.setAlias(ESStorage.readIndexFromDataspace(dataspace));
				}
				pc.setDebug(true);			
				IESResponse cres = pc.get();
				cres.check();
			}

			// register some standard event types??
			registerDataspace4_mapping(_client, baseIndex);

			// done
			knownBaseIndexes.add(baseIndex);
			return true;
			
		} catch (ESAlreadyExistsException ex) {
			Log.i(LOGTAG, ex); // race condition - harmless
			return false;
		} catch(Throwable ex) {			
			Log.e(LOGTAG, ex);
			// swallow and carry on -- an out of date schema may not be a serious issue
			return false;
		}
	}





	private void registerDataspace4_mapping(ESHttpClient _client, String index) 
	{		
		String esType = ESStorage.ESTYPE;
//		String v = _client.getConfig().getIndexAliasVersion();

		Log.d(LOGTAG, "registerDataspace4_mapping index: "+index);

		PutMappingRequest pm = _client.admin().indices().preparePutMapping(index, esType);
		// See DataLogEvent.COMMON_PROPS and toJson()
		ESType keywordy = new ESType().keyword().norms(false).lock();
		// Huh? Why were we using type text with keyword analyzer??
//				.text().analyzer("keyword")					
//				.fielddata(true);
		ESType props = new ESType()
				.property("k", keywordy)
				.property("v", new ESType().text().norms(false))
				.property("n", new ESType().DOUBLE());
		ESType simpleEvent = new ESType()
				.property(DataLogEvent.EVT, keywordy.copy()) // ?? should we set fielddata=true??
				.property("time", new ESType().date())
				.property(ESStorage.count, new ESType().DOUBLE())
				.property("props", props);		
		// common probs...
		for(Entry<String, Class> cp : DataLogEvent.COMMON_PROPS.entrySet()) {
			// HACK to turn Class into ESType
			ESType est = keywordy.copy();
			if (cp.getValue()==StringBuilder.class) {
				est = new ESType().text().norms(false);
			} else if (cp.getValue()==Time.class) {
				est = new ESType().date();
			} else if (cp.getValue()==Double.class) {
				est = new ESType().DOUBLE();
			} else if (cp.getValue()==Integer.class) {
				est = new ESType().INTEGER();
			} else if (cp.getValue()==Long.class) {
				est = new ESType().LONG();					 
			} else if (cp.getValue()==Object.class) {
				if ("geo".equals(cp.getKey())) {
					est = new ESType().geo_point();
				}
			} else if (cp.getValue()==Null.class) {
				// HACK primitive or object?
				if ("nonce".equals(cp.getKey())) {
					est = new ESType().keyword().noIndex();
				} else {
					est = new ESType().object().noIndex();
				}
			}
			simpleEvent.property(cp.getKey(), est);
		}
		// unindexed
		simpleEvent.property("unindexed", new ESType().object().noIndex());
				
		pm.setMapping(simpleEvent);
		pm.setDebug(true);
		IESResponse res = pm.get();
		res.check();
		Log.d(LOGTAG, "...registerDataspace4_mapping done: "+pm.getCurl());
	}



	public void prepWriteIndex(ESHttpClient client, Dataspace dataspace, Time now) {
		String ab = activeBase4dataspace.get(dataspace);				
		String baseIndex = baseIndexFromDataspace(dataspace, now);
		if (baseIndex.equals(ab)) {
			return; // all good already :) <-- This should happen 99.9% of the time
		}
		// Do we need to make the base index for this month?
		registerDataspace2(dataspace, baseIndex);
		
		// What is active for writing? 
		String writeIndex = ESStorage.writeIndexFromDataspace(dataspace);
		IndicesAdminClient clientIndices = client.admin().indices();
		IESResponse wa = clientIndices.getAliases(writeIndex).get();
		Set<String> writeIndices = GetAliasesRequest.getBaseIndices(wa);

		if (writeIndices.contains(baseIndex)) {
			Log.d(LOGTAG, "Write index already correct: "+baseIndex+" <- "+writeIndex+" NOT -> "+ab);
			Log.d(LOGTAG, "set (without a swap) dataspace: "+dataspace+" activeBase: "+baseIndex);
			activeBase4dataspace.put(dataspace, baseIndex);
			return;
		}
		// swap the write index over
		IndicesAliasesRequest aliasSwap = clientIndices.prepareAliases();
		aliasSwap.setDebug(true);
		aliasSwap.setRetries(10); // really try again!
		// our new base
		aliasSwap.addAlias(baseIndex, writeIndex);			
		// remove the old ones
		for (String ow : writeIndices) {
			aliasSwap.removeAlias(ow, writeIndex);
		}
		
		aliasSwap.get().check(); //What happens if we fail here??
		
		Log.d(LOGTAG, "set post-swap dataspace: "+dataspace+" activeBase: "+baseIndex);
		activeBase4dataspace.put(dataspace, baseIndex);
	}



}

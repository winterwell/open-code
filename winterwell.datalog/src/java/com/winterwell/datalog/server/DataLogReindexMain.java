package com.winterwell.datalog.server;

import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.Dataspace;
import com.winterwell.datalog.ESDataLogIndexManager;
import com.winterwell.datalog.ESStorage;
import com.winterwell.datalog.IDataLog;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.ESHttpRequest;
import com.winterwell.es.client.ReindexRequest;
import com.winterwell.es.client.admin.DeleteIndexRequest;
import com.winterwell.es.client.admin.IndicesAliasesRequest;
import com.winterwell.utils.io.Option;
import com.winterwell.utils.log.Log;
import com.winterwell.web.app.AMain;
import com.winterwell.web.app.BasicSiteConfig;

/**
 * Utility to reindex.
 * Use-case: to update the mapping for a particular month
 * @author daniel
 *
 */
public class DataLogReindexMain extends AMain<ReindexConfig> {

	public DataLogReindexMain() {
		super("datalogreindex", ReindexConfig.class);
		noJetty = true;
	}
	
	public static void main(String[] args) {
		DataLogReindexMain dlrm = new DataLogReindexMain();
		dlrm.doMain(args);
	}
	
	@Override
	protected void init2(ReindexConfig config) {
		super.init2(config);
		init3_ES();
	}
	
	protected void doMain2() {
		ESHttpClient esjc = new ESHttpClient();
		
		// make the mapping for dest
		IDataLog dli = DataLog.getImplementation();
		ESStorage ess = (ESStorage) dli.getStorage();
		ESDataLogIndexManager esdim = ess.getESDataLogIndexManager();
		esdim.registerDataspace2(config.dataspace, config.dest);
		
		// Reindex
		ReindexRequest rr = new ReindexRequest(esjc, config.src, config.dest);
		rr.setDebug(true);		
		doIt(rr);
		
		// delete and alias?!
		if (config.replace) {
			DeleteIndexRequest del = esjc.admin().indices().prepareDelete(config.src);
			del.setDebug(true);
			doIt(del);
			
			IndicesAliasesRequest alias = esjc.admin().indices().prepareAliases();
			alias.addAlias(config.dest, config.src);
			doIt(alias);
		}
	
		stop();
	}

	private void doIt(ESHttpRequest rr) {
		if (config.echo) {			
			System.out.println(rr.getCurl());
			Log.i(rr.getCurl());
		} else {
			rr.get().check();
		}
	}
	
}


class ReindexConfig extends BasicSiteConfig {
	
	@Option
	boolean replace;
	
	@Option(required=true)
	public String dest;
	
	@Option(required=true)
	public String src;
	
	@Option
	Dataspace dataspace;
	
	@Option(description="If set, dont run the reindex - just echo a curl to sysout and log")
	boolean echo;
}

package com.winterwell.web.app;

import java.util.Map;

import com.winterwell.data.AThing;
import com.winterwell.data.KStatus;
import com.winterwell.es.XIdTypeAdapter;
import com.winterwell.gson.Gson;
import com.winterwell.gson.GsonBuilder;
import com.winterwell.gson.StandardAdapters;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.AString;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.gui.GuiUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.WebUtils;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.ajax.JThing;
import com.winterwell.web.data.XId;
import com.winterwell.youagain.client.App2AppAuthClient;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * Status: WIP 
 * A java client for working with data managed by a {@link CrudServlet}
 * @author daniel
 *
 * @param <T> the data-item managed
 */
public class CrudClient<T> {

	@Override
	public String toString() {
		return getClass().getSimpleName()+"[endpoint=" + endpoint + "]";
	}

	private Class<T> type;
	private String endpoint;
	private boolean debug;

	/**
	 * HACK copy an item from e.g. test to live, or vice-versa
	 * @param id
	 * @param src
	 * @param dest
	 */
	public void copyAcrossServers(String id, KServerType src, KServerType dest) {
		setDebug(true);
		String _endpoint = endpoint;
		try {
			setServerType(src);
			JThing<T> srcItem = get(id);
			setServerType(dest);
			publish(srcItem.java());
		} finally {
			endpoint = _endpoint;
		}
	}
	
	/**
	 * @deprecated For testing only
	 * @param src
	 * @return
	 */
	public String setServerType(KServerType src) {
		String _endpoint = endpoint.replaceFirst("local|test", "");
		switch(src) {
		case PRODUCTION:
			break;
		case TEST:
			_endpoint = _endpoint.replace("//", "//test");
			break;
		case LOCAL:
			_endpoint = _endpoint.replace("//", "//local");
			break;
		}
		// HACK Sogive is different
		if (endpoint.contains("sogive")) {
			 switch(src) {
			 case PRODUCTION:
				_endpoint = "https://app.sogive.org/charity";
				break;
			 case TEST:
				 _endpoint = "https://test.sogive.org/charity";
				break;
			case LOCAL:	// https not working for local - DW Jan 2022
				_endpoint = "http://local.sogive.org/charity";
				break;
			}	 			 
		}
		this.endpoint = _endpoint;
		return endpoint;
	}
	

	public CrudClient<T> setDebug(boolean b) {
		debug = b;
		return this;
	}	
	
	/**
	 * @deprecated Normally this is set from config.
	 * @param endpoint
	 */
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}
	
	public String getEndpoint() {
		return endpoint;
	}
	
	/**
	 * Without this, expect an auth error!
	 * 
	 * We want a JWT that says:
	 * "I am app A" (identity) 
	 * and 
	 * "I, Bob, give app A permission to manage T" (permission)
	 */
	private String jwt;
	
	protected boolean authNeeded;

	/**
	 * Set authentication! Without this, expect an auth error.
	 * @param jwt
	 */
	public void setJwt(String jwt) {
		this.jwt = jwt;
	}
	

	/**
	 * Check for a stored token 
	 * @param issuer / dataspace e.g. "good-loop"
	 * @param product e.g. "myapp.example.com"
	 */
	public void doAuth(CharSequence issuer, String product) {
		YouAgainClient yac = new YouAgainClient(issuer, product);
		AuthToken token = yac.loadLocal(new XId(product+"@app"));
		if (token == null) {
			// try for a more general issuer-level token?
			token = yac.loadLocal(new XId(issuer+"@app"));
		}
		if (token == null) {
			if ( ! GuiUtils.isInteractive()) {
				Log.d(getClass().getSimpleName()+" cant get auth token for "+issuer+" "+product);
			}
			Log.d(getClass().getSimpleName()+" Ask user for auth token for "+issuer+" "+product);
			App2AppAuthClient a2a = yac.appAuth();
			String appAuthPassword = GuiUtils.askUser("Password for "+issuer); 
			token = a2a.registerIdentityTokenWithYA(product, appAuthPassword);
			yac.storeLocal(token);
		}
		Log.d("init.auth", "AuthToken set from loadLocal .token folder "+token.getXId());
//		Dep.set(AuthToken.class, token);		
		setJwt(token.getToken());
	}
	
	public boolean isAuthorized() {
		return jwt!=null;
	}

	
	/**
	 * 
	 * @param type
	 * @param endpoint The endpoint for this type, e.g. "https://myserver.com/mytype"
	 */
	public CrudClient(Class<T> type, String endpoint) {
		this.type = type;
		this.endpoint = endpoint;
		Utils.check4null(type, endpoint);
	}

	public JSend<ListHits<T>> list() {
		FakeBrowser fb = fb();
		
		String response = fb.getPage(endpoint+"/"+CrudServlet.LIST_SLUG, params);
		
		JSend jsend = jsend(fb, response);
		if (jsend.isSuccess()) {
			jsend.getData().setType(ListHits.class);
		}
		return jsend;
	}
	
	Map<String, String> params;

	/**
	 * 
	 * @param id
	 * @return object (throws 404 if not found)
	 * @throws WebEx.E404
	 */
	public JThing<T> get(String id) throws WebEx.E404 {
		FakeBrowser fb = fb();
		String response = fb.getPage(endpoint+"/"+WebUtils.urlEncode(id), params);
		
		JSend jsend = jsend(fb, response);
		JThing<T> jt = jsend.getData();
		jt.setType(type);
		return jt;
	}
	
	/**
	 * Lenient convenience for {@link #get(String)}
	 * @param id Can be null
	 * @return object or null
	 */
	public T getIfPresent(String id) {
		if (Utils.isBlank(id)) return null;
		try {
			JThing<T> jobj = get(id);
			return jobj.java();
		} catch (WebEx.E404 e) {
			return null;
		}
	}

	
	public JSend publish(T item) {
		FakeBrowser fb = fb();
		
		Gson gson = gson();
		String sjson = gson.toJson(item);
		Map<String, String> vars = new ArrayMap(
			WebRequest.ACTION_PARAMETER, CrudServlet.ACTION_PUBLISH,
			AppUtils.ITEM.getName(), sjson
		);
		String url = endpoint;
		// ID?
		String id = getId(item);
		if (id != null) {
			String encId = WebUtils.urlEncode(id);
			url += "/"+encId;
		}
		
		String response = fb.post(url, vars);

		JSend jsend = jsend(fb, response);
		return jsend;
	}
	
	public JSend saveDraft(T item) {
		FakeBrowser fb = fb();
		
		Gson gson = gson();
		String sjson = gson.toJson(item);
		Map<String, String> vars = new ArrayMap(
			WebRequest.ACTION_PARAMETER, CrudServlet.ACTION_SAVE,
			AppUtils.ITEM.getName(), sjson
		);
		String url = endpoint;
		// ID?
		String id = getId(item);
		if (id != null) {
			String encId = WebUtils.urlEncode(id);
			url += "/"+encId;
		}
		
		String response = fb.post(url, vars);

		JSend jsend = jsend(fb, response);
		return jsend;
	}

	private JSend jsend(FakeBrowser fb, String response) {
		Gson gson = gson();
		Map data = gson.fromJson(response);
		JSend jsend = JSend.parse2_create(data);
				
		jsend.setCode(fb.getStatus());
		return jsend;
	}

	private FakeBrowser fb() {
		FakeBrowser fb = new FakeBrowser();
		fb.setDebug(debug);
		fb.setRetryOnError(2); // 3 tries
		// You really should set auth!
		if (jwt != null) {
			fb.setAuthenticationByJWT(jwt);
		} else {
			if (authNeeded) throw new WebEx.E401("No authentication set for "+this+" - call setJwt()");
		}
		return fb;
	}

	protected String getId(T item) {
		if (item instanceof AThing) {
			return ((AThing) item).getId();
		}
		return null;
	}

	protected Gson gson() {
		Gson gson = Dep.getWithDefault(Gson.class, null);
		if (gson==null) {
			// make one that can handle XId
			GsonBuilder gb = new GsonBuilder();
			gb.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter());
			gb.registerTypeAdapter(XId.class, new XIdTypeAdapter());
			gb.registerTypeHierarchyAdapter(AString.class, new StandardAdapters.ToStringSerialiser());
//			gb.serializeSpecialFloatingPointValues(); // we prob dont want to send NaN
			gb.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			gson = gb.create();
		}
		return gson;
	}

	public void setStatus(KStatus pubOrDraft) {
		if (params==null) params = new ArrayMap();
		params.put("status", pubOrDraft.toString());
	}

	/**
	 * @deprecated {@link #searchHits(SearchQuery)}
	 * @param sq
	 * @return
	 */
	public JSend search(SearchQuery sq) {
		FakeBrowser fb = fb();
		ArrayMap qparams = new ArrayMap(params);
		qparams.put(CommonFields.Q.name, sq.getRaw());
		String response = fb.getPage(endpoint+"/"+CrudServlet.LIST_SLUG, qparams);
		
		JSend jsend = jsend(fb, response);
		return jsend;
	}

	public CrudSearchResults<T> searchHits(SearchQuery sq) {
		JSend res = search(sq);
		JThing<CrudSearchResults<T>> jthing = res.getData().setType(CrudSearchResults.class);
		CrudSearchResults<T> csr = jthing.java();
		return csr;
	}
	
	public Class<T> getType() {
		return type;
	}
	
}

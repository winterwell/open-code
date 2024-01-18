package com.winterwell.youagain.client;

import java.io.File;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.winterwell.utils.Dep;
import com.winterwell.utils.FailureException;
import com.winterwell.utils.Key;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.ArraySet;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.Pair;
import com.winterwell.utils.io.ConfigFactory;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.utils.web.XStreamUtils;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.AjaxMsg;
import com.winterwell.web.ajax.AjaxMsg.KNoteType;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.app.Logins;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.data.XId;
import com.winterwell.web.fields.AField;
import com.winterwell.web.fields.MissingFieldException;
import com.winterwell.web.fields.XIdField;

import jakarta.mail.internet.InternetAddress;

/**
 * Query YouAgain from Java. E.g. check that tokens are valid.
 * 
 * This is a thread-safe and lightweight object.
 * 
 * It loads a {@link YouAgainClientConfig} config via {@link ConfigFactory}
 * 
 * It handles 2 use-cases:
 *
 * 1. a java web-service wants to verify an incoming user request.
 * 2. a java app wants to authenticate _itself_ for connecting with a 2nd app. 
 * 
 * @testedyb {@link YouAgainClientTest}
 * @author daniel
 */
public final class YouAgainClient {

	public String getIssuer() {
		return iss;
	}
	
	public static XId xidFromEmail(String email) {
		return new XId(WebUtils2.canonicalEmail(email), "email");
	}
	
	public static XId xidFromEmail(InternetAddress email) {
		return new XId(WebUtils2.canonicalEmail(email), "email");
	}

	
	/** 
	 * @param txid
	 * @param appOwnerAuth email & password for an app-owner
	 * @return oauth tokens for txid, if known
	 */
	// ??add to AuthToken??
	public String[] getOAuthTokens(XId txid, LoginDetails appOwnerAuth) {
		FakeBrowser fb = new FakeBrowser();
		fb.setAuthentication(appOwnerAuth.loginName, appOwnerAuth.password);
		fb.setDebug(debug);
		String endpoint_AUTH = yac.endpoint.replace("youagain.json", "auth.json");
		String response = fb.getPage(endpoint_AUTH, new ArrayMap(
				"app", iss,
				"txid", txid));
		JSend jsend = JSend.parse(response);
		if ( ! jsend.isSuccess()) {
			throw new FailureException(txid+" -> "+jsend.getMessage());
		}
		String authxml = (String) jsend.getData().map().get("auth");
		Object tokens = XStreamUtils.serialiseFromXml(authxml);
		return (String[]) tokens;
	}
	
	/**
	 * NB: This is not an AField as it is only used for local within-the-request caching
	 */
	private static final Key<List<AuthToken>> AUTHS = new Key("ya_auths");

	private static final String LOGTAG = "youagain";
	
	/**
	 * @Deprecated This is the YA app itself
	 * 
	 * TODO shouldnt this be youagain.good-loop.com?? But would that break existing login setups??
	 */
	public static final String MASTERAPP = "youagain";
	
	/**
	 * What dataspace are you using? e.g. "sogive" or "good-loop".
	 * Each app has its own namespace for auth data.
	 */
	final String iss;

	private boolean debug;

	YouAgainClientConfig yac;
	private boolean initFlag;

	/**
	 * not used yet - distinguish between parts of a broader platform
	 */
	private String product;
	
	/**
	 * 
	 * @param issuer e.g. `good-loop` The dataspace
	 * @param product e.g. `profiler.good-loop.com` -- not an XId. Can be null.
	 * This is the product that is using this client object to make requests.
	 */
	public YouAgainClient(CharSequence issuer, String product) {
		if (Utils.isBlank(issuer)) {
			throw new MissingFieldException(new AField("d"), "no issuer/dataspace?! product:"+product);		
		}
		this.iss = issuer.toString();
		assert ! iss.endsWith("@app") : "This should not be an xid "+issuer;
		this.product = product;
		init();
		setDebug(true); // FIXME
	}
	
	/**
	 * @deprecated
	 * @param app e.g. `profiler.good-loop.com` -- not an XId
	 */
	public YouAgainClient(String app) {
		this(app, app);
	}	
	
	/**
	 * Allows for config to override the yac.endpoint used
	 */
	private void init() {
		if (initFlag) return;		
		initFlag = true;
		try {			
			if (Dep.has(YouAgainClientConfig.class)) {
				yac = Dep.get(YouAgainClientConfig.class);
			} else {
				ConfigFactory cf = ConfigFactory.get();
				yac = cf.getConfig(YouAgainClientConfig.class);
			}
			assert ! Utils.isBlank(yac.endpoint) : yac;
		} catch(Throwable ex) {
			Log.e(LOGTAG, ex); // swallow
		}
	}

	/**
	 * @deprecated Only used for testing
	 * @param yacc
	 */
	public void setConfig(YouAgainClientConfig yacc) {
		this.yac = yacc;
	}
	
	@Override
	public String toString() {
		return "YouAgainClient [d=" + iss + "]";
	}
	
	/**
	 * See TrackingPixelServlet
	 */
	public static final String TRK_APP = "trk";
			
	/**
	 * This is the method you want :)
	 * 
	 * This will also call state.setUser(). 
	 * Caches the return so repeated calls are fast.
	 * @param state
	 * @return List of AuthTokens. Never null.
	 * WARNING: This can include anonymous temporary "nonce@temp" tokens!
	 * The list is a fresh ArrayList which can be modified without side-effects.
	 */
	public List<AuthToken> getAuthTokens(WebRequest state) {
		// check cache
		// NB: using deprecated low-level getProperties() to avoid the odd bug seen below
		Object _auths = state.getProperties().get(AUTHS);
		if (_auths instanceof String) { // paranoia - ClassCast bug seen Apr 2021
			// Dec 2021: somehow "ya_auths=****" is getting into either cookies or url params?!  
			Log.w(LOGTAG, "(skip cache) String not list?! "+AUTHS+": "+_auths+" from "+state);
			_auths = null; // skip the cache then			
		}		
		List<AuthToken> tokens = (List<AuthToken>) _auths;
		if (tokens!=null) {
			return new ArrayList(tokens);
		}
		
		// all jwt
		List<String> jwt = getAllJWTTokens(state);
		
		// basic auth?
		AuthToken basicToken = null;
		Pair<String> np = WebUtils2.getBasicAuthentication(state.getRequest());
		if (np !=null) {
			// verify it
			basicToken = verifyNamePassword(np.first, np.second);
		}
		if ( ! jwt.isEmpty()) {
			// verify the tokens
			tokens = verify(jwt, state);
		} else {
			// just name/password
			tokens = new ArrayList();
		}
		assert(tokens!=null);
		
		// add the name/password user first, if set
		if (basicToken!=null) {
			tokens.add(0, basicToken);
		}
		
		// add tracking cookie		
		String trkId = state.getCookie("trkid");
		if (trkId != null) {
			String token = null; // NB: to get the right AuthToken constructor
			AuthToken ta = new AuthToken(token).setApp(TRK_APP).setXId(new XId(trkId, false));
			tokens.add(ta);
		}
		
		// stash them
		state.getProperties().put(AUTHS, tokens);
		// set user?
		getAuthTokens2_maybeSetUser(state, tokens);
		// done
		return new ArrayList(tokens);
	}
	
	/**
	 * Set user if tokens and not already set
	 * @param state
	 * @param tokens
	 */
	private void getAuthTokens2_maybeSetUser(WebRequest state, List<AuthToken> tokens) {
		if (tokens.isEmpty() || state.getUser()!=null) {
			return;
		}
		AuthToken user = tokens.get(0);
		final XId uxid = state.get(new XIdField("uxid"));
		if (uxid!=null) {
			user = Containers.first(tokens, t -> t.getXId().equals(uxid));
			if (user==null) {
				Log.d(LOGTAG, "Unauthorised uxid "+uxid+" with "+tokens);
				user = tokens.get(0);
			}
		}
		state.setUser(user.getXId(), user);
	}

	private AuthToken verifyNamePassword(String email, String password) {
		if (verifyOff) {
			AuthToken at = new AuthToken(new ArrayMap("xid", email+"@email"));
			return at;
		}
		Utils.check4null(email, password);
		FakeBrowser fb = new FakeBrowser();
		fb.setDebug(debug);
		fb.setAuthentication(email, password);
		String response = fb.getPage(yac.endpoint, new ArrayMap(
				"d", iss, 
				"action", "login" 
				));		
		Map user = userFromResponse(response);
		AuthToken at = new AuthToken(user);
		return at;
	}

	/**
	 * Only for testing
	 */
	@Deprecated
	private boolean verifyOff;	
	
	/**
	 * 
	 * @param jwt
	 * @param state Can be null. For sending messages back
	 * @return verified auth tokens and unverified nonce@temp tokens. Never null.
	 */
	public List<AuthToken> verify(List<String> jwt, WebRequest state) {
		Log.d(LOGTAG, "verify: "+jwt);
		final List<AuthToken> list = new ArrayList();
		if (jwt.isEmpty()) return list;
		for (String jt : jwt) {
			try {
				AuthToken token = new AuthToken(jt);
				// HACK include temp tokens!
				if (isTempId(jt)) {
					token.xid = new XId(jt, false);
					list.add(token);
					continue;
				}
				if (verifyOff) {
					list.add(token);
					continue;
				}
				// TODO a better appraoch would be for the browser to make a proper JWT for @temp

				// decode the token
				JWTDecoder dec = getDecoder();
				dec.debug = state==null? false : state.debug;
				DecodedJWT decd = dec.decryptJWT(jt);
				token.xid = new XId(decd.getSubject(), false);
				token.verified = true;
				list.add(token);
			} catch (Throwable e) {
				Log.i(LOGTAG, e);
				// issuer mismatch is fine - e.g. a SoGive + Good-Loop user 
//				// pass back to the user but keep on trucking
				if (state!=null) {
					state.addMessage(new AjaxMsg(KNoteType.warning, "JWT token error", e.toString()));
				}
			}
		}
		return list;		
	}

	private boolean isTempId(String jt) {
		return jt!=null && jt.endsWith("@temp");
	}


	JWTDecoder _decoder;
	
	/**
	 * NB: the signing key is the youagain key, shared by all apps
	 */
	static PublicKey yaPubKey;
	
	public JWTDecoder getDecoder() throws Exception {
		if (_decoder!=null) return _decoder;		
		_decoder = new JWTDecoder(iss);
		if (yaPubKey==null) {
			String publickeyendpoint = yac.endpoint.replace("youagain.json", "publickey");
			// load from the server, so we could change keys			
			String skey = new FakeBrowser().getPage(publickeyendpoint);
			yaPubKey = JWTDecoder.keyFromString(skey);
			Log.d(LOGTAG, "GOT key "+yaPubKey+" from "+publickeyendpoint);	
		}
		_decoder.setPublicKey(yaPubKey);
		return _decoder;
	}

	/**
	 * Low-level access to JWT tokens. Use {@link #getAuthTokens(WebRequest)} instead.
	 * https://en.wikipedia.org/wiki/JSON_Web_Token
	 * @param state
	 * @return
	 */
	public List<String> getAllJWTTokens(WebRequest state) {		
		Collection<String> all = new ArrayList();		
		// Auth header(s)-- multiple should be by comma, could be by multiple headers
		Enumeration<String> authHeaders = state.getRequest().getHeaders("Authorization");
		if (authHeaders!=null) {
			List<String> _authHeaders = Containers.asList(authHeaders);
			for (String authheader : _authHeaders) {
				authheader = authheader.trim();
				// split on comma (c.f. https://stackoverflow.com/questions/29282578/multiple-http-authorization-headers)
				String[] authHeaders2 = authheader.split(", ");
				for (String ah : authHeaders2) {
					if (ah.startsWith("Bearer")) {
						String jwt = ah.substring("Bearer".length(), ah.length()).trim();
						if (state.debug) Log.d(LOGTAG, "JWT from auth-header Bearer: "+jwt);
						all.add(jwt);
					}				
				}
			}
		}		
		// NB: This isnt standard, this is our naming rule  blah.jwt
		// see LoginPlugin.getJWTCookieName()
//		String app = YouAgainServlet.getApp(state);
//		Dataspace d = YouAgainServlet.getDataspace(state);
		Pattern KEY = Pattern.compile("^([a-zA-Z0-9\\-_\\.]+\\.)?jwt");
		// cookies
		Map<String, String> cookies = WebUtils2.getCookies(state.getRequest());
		for(String c : cookies.keySet()) {
			if ( ! KEY.matcher(c).find()) continue; 
			String jwt = cookies.get(c);
			if (state.debug) Log.d(LOGTAG, "JWT from cookie "+c+": "+jwt);
			all.add(jwt);
		}				
		// and parameters which match blah.jwt
		Map<String, Object> pmap = state.getParameterMap();
		for(String c : pmap.keySet()) {
			if ( ! KEY.matcher(c).find()) continue;		
			Object jwt = pmap.get(c);
			// is it a list??
			if (jwt instanceof String[]) {
				List<String> jwts = Containers.asList((String[])jwt);
				if (state.debug) Log.d(LOGTAG, "JWTs from parameter "+c+": "+jwts);				
				all.addAll(jwts);	
			} else {
				if (state.debug) Log.d(LOGTAG, "JWT from parameter "+c+": "+jwt);	
				all.add((String)jwt);
			}
		}
		// unpack any lists
		// since , [ are not valid base64, this is safe and easy
		ArraySet<String> all2 = new ArraySet();
		for (String jwt : all) {
			if (jwt.startsWith("[")) {
				try {
					List jwts = WebUtils2.parseJSON(jwt);
					all2.addAll(jwts);
				} catch (Exception ex) {
					Log.w(LOGTAG, "JWT parse error: "+ex+" from "+jwt);
				}
			} else if (jwt.contains(",")) {
				List jwts = StrUtils.splitOnComma(jwt);
				all2.addAll(jwts);
			} else {
				all2.add(jwt);
			}
		}
		// filter out any blanks
		List<String> all3 = Containers.filter(all2, jwt -> ! Utils.isBlank(jwt));
		// done
		return all3;
	}

	/**
	 * 
	 * @param usernameUsuallyAnEmail or an XId
	 * @param password
	 * @return
	 * @throws LoginFailedException
	 */
	public AuthToken login(String usernameUsuallyAnEmail, String password) throws LoginFailedException {
		try {
			Utils.check4null(usernameUsuallyAnEmail, password);
			FakeBrowser fb = new FakeBrowser();
			String response = fb.getPage(yac.endpoint, new ArrayMap(
					"d", iss,
					"action", "login",
					"person", usernameUsuallyAnEmail,
					"password", password));
			Map user = userFromResponse(response);
			AuthToken at = new AuthToken(user);
			return at;
		} catch(WebEx wex) {
			throw new LoginFailedException(wex.getMessage());
		}
	}

	public AuthToken register(XId xid, String password) {
		return register(xid.toString(), password);
	}
	
	public AuthToken register(String usernameUsuallyAnEmail, String password) {
		assert yac != null;
		Utils.check4null(usernameUsuallyAnEmail, password);
		FakeBrowser fb = new FakeBrowser();
		
		fb.setDebug(true); // TODO remove
		
		String response = fb.getPage(yac.endpoint, new ArrayMap(
				"d", iss,
				"action", "signup",
				"person", usernameUsuallyAnEmail,
				"password", password));
		Map user = userFromResponse(response);
		AuthToken at = new AuthToken(user);
		return at;
	}
	

	public Object delete(XId user, AuthToken at) {
		assert yac != null;
		Utils.check4null(user, at);
		FakeBrowser fb = fb(Collections.singletonList(at));
		fb.setDebug(true);
		Log.d(LOGTAG, "Deleting "+user+" auth: "+at+"...");
		String response = fb.getPage(yac.endpoint, new ArrayMap(
				"d", iss,
				"action", "delete",
				"person", user.toString())
				);
		Log.d(LOGTAG, "Deleted? "+user+" Response: "+response);
		return response;
	}

	FakeBrowser fb(List<AuthToken> at) {
		FakeBrowser fb = new FakeBrowser();
		List<String> ats = Containers.apply(at, AuthToken::getToken);
		fb.setAuthenticationByJWTs(ats);
		fb.setDebug(debug);
		return fb;
	}

	private Map userFromResponse(String response) {
		JSend jsend = JSend.parse(response);
		Map cargo = jsend.getData().map();
		Map user = (Map) cargo.get("user");
		return user;
	}

	/**
	 * If uxid is specified use that (testing for a matching auth token!),
	 * otherwise return the first auth-token,
	 * or null.
	 * @return the user this request should be treated as being from.
	 */
	public XId getUserId(WebRequest state) {
		List<AuthToken> auths = getAuthTokens(state);
		return getUserId2(state, auths);
	}
	
	/**
	 * @param state
	 * @param auths
	 * @return
	 */
	private XId getUserId2(WebRequest state, List<AuthToken> auths) {
		XId uxid = state.get(new XIdField("uxid"));
		// ?? verify uxid matches an auth token??
		if (uxid==null) {
			// no user?
			if (auths==null || auths.isEmpty()) {
				return null;
			}			
			uxid = auths.get(0).xid;
		} else {
			if (auths==null) throw new WebEx.E401(state.getRequestUrl(), 
					"No auth-tokens. Can't act as "+uxid);
		}
		assert uxid != null;
		// FIXME security check
//		AuthToken auth = Containers.first(auths, a -> a.xid.equals(uxid));
//		if (auth==null) {
//			throw new WebEx.E401(state.getRequestUrl(), "No auth-token for "+uxid);
//		}
		// done
		return uxid;
	}
	
	public ShareClient sharing () {
		return new ShareClient(this);
	}
	
	public void setDebug(boolean b) {
		this.debug = b;
	}


	public AuthToken login(XId xid, String password) {
		return login(xid.toString(), password);
	}

	public void storeLocal(AuthToken token) {
		yac.localTokenStore.mkdirs();
		String xml = XStreamUtils.serialiseToXml(token);
		XId xid = token.getXId();
		File out = storeLocal2(xid);		
		FileUtils.write(out, xml);
		// for GL engineers
		Log.d(LOGTAG,"token stored in "+out+". You may wish to copy it to "+Logins.getLoginFile("youagain", out.getName()));
	}
	
	private File storeLocal2(XId xid) {
		return new File(yac.localTokenStore, 
			FileUtils.safeFilename(xid.getName())
			+"@"
			+FileUtils.safeFilename(xid.service)
		);
	}

	/**
	 * 
	 * @param xid
	 * @return
	 * @see #storeLocal(AuthToken)
	 */
	public AuthToken loadLocal(XId xid) {
		File out = storeLocal2(xid);		
		if (! out.isFile()) {
			// HACK try Logins
			File out2 = Logins.getLoginFile("youagain", out.getName());
			if ( ! out2.isFile()) {
				return null;
			}
			out = out2;
		}
		String xml = FileUtils.read(out);
		if (Utils.isBlank(xml)) return null;
		Log.d(LOGTAG, "loadLocal: "+xid+" "+out);
		AuthToken at = XStreamUtils.serialiseFromXml(xml);
		return at;
	}

	public App2AppAuthClient appAuth() {
		return new App2AppAuthClient(this);
	}

	public List<String> getRoles(List<AuthToken> auths) {
		List<String> shares = sharing().getSharedWith(auths, "role:");		
		List<String> roles = Containers.filterNulls(
				Containers.apply(shares, s -> s.startsWith("role:")? s.substring(5) : null)
				);
		roles = new ArrayList(new HashSet(roles)); // de dupe
		return roles;
	}

	/**
	 * 
	 * @param tokens
	 * @param xid
	 * @return true if tokens contains a (TODO verified) token for xid
	 */
	public boolean hasAuthTokenFor(List<AuthToken> tokens, XId xid) {
		for (AuthToken authToken : tokens) {
			if (xid.equals(authToken.xid)) {
				// TODO check for verified and in-date
				return true;
			}
		}
		return false;
	}

	

}

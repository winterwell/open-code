package com.winterwell.youagain.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.winterwell.utils.Dep;
import com.winterwell.utils.IProperties;
import com.winterwell.utils.Key;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.data.IHasXId;
import com.winterwell.web.data.XId;

/**
 * Use {@link JWTEncoder} to make.
 * 
 * TODO merge with DBAuth and make both JWT token based
 * 
 * A token identifying and authorising.
 * @author daniel
 */
public class AuthToken implements IHasXId, IProperties {

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((app == null) ? 0 : app.hashCode());
		result = prime * result + getXId().hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AuthToken other = (AuthToken) obj;
		if (app == null) {
			if (other.app != null)
				return false;
		} else if (!app.equals(other.app))
			return false;
		if (xid == null) {
			if (other.xid != null)
				return false;
		} else if (!xid.equals(other.xid))
			return false;
		return true;
	}
	
	private String app;
	

	/**
	 * Optional Display name - not user-name!
	 */
	public String name;
	
	/**
	 * @return a JWT token
	 */
	public String getToken() {
		if (token==null) {			
			try {
				// this shouldn't happen, but I guess its OK to make a fresh token
				JWTEncoder enc = new JWTEncoder(app);
				token = enc.encryptJWT(xid);
			} catch (Exception e) {
				throw Utils.runtime(e);
			}
		}
		return token;
	}
	
	public AuthToken(String token) {
		this.token = token;
	}
	
	/**
	 * 
	 * @param jsonObj {jwt, xid}
	 */
	public AuthToken(Map jsonObj) {
		this.token = (String) jsonObj.get("jwt");
		Object _xid = jsonObj.get("xid");
		this.xid = XId.xid(_xid);
	}
	/**
	 * A token which can be verified with the YouAgain server.
	 * 
	 * Can be null for tracking-tokens
	 */
	String token;
	/**
	 * Who is this for?
	 */
	public XId xid;
	
	/**
	 * OPtional url for a profile image
	 */
	public String img;


	transient boolean verified;
	
	/**
	 * Has this been (locally, transient) verified by decoding it? 
	 * @return true means it should be OK. false -- maybe verify?
	 */
	public boolean isVerified() {
		return verified;
	}
	
	@Override
	public String toString() {
		return "AuthToken["+xid+"]";
	}

	public XId getXId() {
		if (xid==null) {
			try {
				// get from token
				YouAgainClient yac = Dep.get(YouAgainClient.class);
	//			new JWTDecoder(app) objecting to no PublicKey
				JWTDecoder decoder = yac.getDecoder();
				DecodedJWT decd = decoder.decryptJWT(getToken());
				String subj = decd.getSubject();
				xid = new XId(subj, false);
			} catch(Exception ex) {
				throw Utils.runtime(ex);
			}
		}
		return xid;
	}

	public AuthToken setApp(String app) {
		this.app = app;
		return this;
	}

	public AuthToken setXId(XId xid) {
		this.xid = xid;
		return this;
	}

	@Override
	public <T> T get(Key<T> key) {
		String k = key.name;;
		return (T) Containers.objectAsMap(this).get(k);
	}

	@Override
	public Collection<Key> getKeys() {
		return Containers.apply(
				"app img name token xid".split(" "),
				k -> new Key(k));
	}

	@Override
	public <T> T put(Key<T> key, T value) {
		throw new UnsupportedOperationException("put "+key+" = "+value);
	}

	/**
	 * Set authentication for making requests to other YouAgain services
	 * @param fb
	 * @param auth
	 */
	public static void setAuth(FakeBrowser fb, List<AuthToken> auth) {
		if (auth==null || auth.isEmpty()) {
			return;
		}
		// see https://stackoverflow.com/questions/29282578/multiple-http-authorization-headers
		StringBuilder tokens = new StringBuilder();		
		for (AuthToken authToken : auth) {
			tokens.append("Bearer "+authToken.getToken()+", ");
		}
		StrUtils.pop(tokens, 2);
		fb.setRequestHeader("Authorization", tokens);
	}

	/**
	 * @return true if this is for a temporary ID, eg a session cookie.
	 * 
	 * See TrackingPixelServlet which makes nonce@trk cookie ids
	 */
	public boolean isTemp() {
		if (getXId().isService("temp") || getXId().isService("trk")) {
			return true;
		}
		return false;
	}

	public void setVerified(boolean verified) {
		this.verified = verified;
	}
	
}

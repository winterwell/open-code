package com.winterwell.data;

import java.util.List;

import com.winterwell.depot.IInit;
import com.winterwell.es.ESKeyword;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.web.data.XId;
import com.winterwell.youagain.client.ShareToken;

/**
 * Base class for things.
 * {created, id, name, shares, status, url}
 * Goal: loosely base on https://schema.org/Thing
 * @author daniel
 *
 */
public class AThing implements IInit {

	/**
	 * @deprecated TODO fill in from ES
	 */
	transient int _version;
	
	/**
	 * Note: sadly this is not present on data before April 2020
	 * Old items when loaded into memory may pick up a false date of today.
	 */
	Time created = new Time(); 
	

	/**
	 * Normally an XId (so that it has service, and is in a canonical form for that service)
	 */
	public String id;
	
	/**
	 * TODO How can we auto set this?
	 * Currently autoset by {@link com.winterwell.web.app.AppUtils#doSaveEdit(com.winterwell.es.ESPath, com.winterwell.web.ajax.JThing, com.winterwell.web.app.WebRequest)}
	 */
	Time lastModified;
	
	public String name;
	
	/** Owner XID - users should only see their own tags
	 * 
	 * ??replace with shares ShareToken?? Or keep as a simpler alternative to ShareToken for many uses??
	 * */
	public XId oxid;

	/**
	 * Cache of shares - YouAgain is the definitive source, but we can store in
	 * the DB for speedy filtering.
	 * 
	 * TODO have YA make calls to set these (using an endpoint registered against an app).
	 * TODO but the app should have logic to create shares, e.g. "an anon-xid donation is shared with the donor's email"
	 */
	private List<ShareToken> shares;
	
	KStatus status;

	
	@ESKeyword
	public String url;
	
	/**
	 * class + id
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AThing other = (AThing) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
	
	public Time getCreated() {
		return created;
	}
	
	public String getId() {
		return id;
	}

	/**
	 * 
	 * @return
	 */
	public Time getLastModified() {
		return lastModified;
	}

	public String getName() {
		return name;
	}

	/**
	 * @deprecated YA is the definitive source. This is a cache.
	 * @return
	 */
	public List<ShareToken> getShares() {
		return shares;
	}

	public KStatus getStatus() {
		return status;
	}

	public String getUrl() {
		return url;
	}

	/**
	 * 
	 * @return default pattern: if id has an @, use it - otherwise add "@type.good-loop.com"
	 */
	public XId getXId() {
		if (XId.XID_PATTERN.matcher(getId()).matches()) {
			return new XId(getId());
		}
		String type = getClass().getSimpleName().toLowerCase();
		String service = type+".good-loop.com";
		return new XId(getId(), service, false);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	/**
	 * Check (and patch) the data in this Thing.
	 * @return this
	 */
	public void init() {		
	}
	
	protected String LOGTAG() {
		return getClass().getSimpleName();
	}
	public void setCreated(Time created) {
		this.created = created;
	}
	public void setId(String id) {
		if (this.id!=null && ! this.id.equals("new") && ! id.equals(this.id)) {
			Log.w(LOGTAG(), "Change id from "+this.id+" to "+id+" "+ReflectionUtils.getSomeStack(10));
		}
		this.id = id;
	}
	
	public void setLastModified(Time lastModified) {
		this.lastModified = lastModified;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setShares(List<ShareToken> shares) {
		this.shares = shares;
	}

	/**
	 * Set the local flag. Does NOT trigger a save or any database level action.
	 * @param status
	 */
	public void setStatus(KStatus status) {
		this.status = status;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName()+"[name=" + name + ", id=" + id + ", status=" + status
				+ "]";
	}
	
	
}



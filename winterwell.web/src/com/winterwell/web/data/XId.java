package com.winterwell.web.data;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Warning;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.IHasJson;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.LoginDetails;

/**
 * An id for an external service.
 *
 *
 * 
 * @see DBLogin (database backed)
 * @see LoginDetails (this is lighter and a bit different)
 * @author daniel
 * @testedby XIdTest
 */
public final class XId implements Serializable, IHasJson, CharSequence, Comparable<XId> {
	private static final long serialVersionUID = 1L;

	/**
	 * Group 1 is the name, group 2 is the service
	 */
	public static final Pattern XID_PATTERN = Pattern.compile(
			"(\\S+)@([A-Za-z\\.]+)?");

	/**
	 * XId for unknown person + unspecified service
	 */
	public static final XId ANON = new XId("anon@unspecified", false);

	/**
	 * Marker for "Please make this!"
	 */
	public static final XId NEW = new XId("_NEW_@unspecified", false);

	/**
	 * The service-specific ID -- e.g. Twitter username 
	 */
	public final String name;
	
	/**
	 * The service. Canonical pattern we'd like to adopt (but mostly dont use at present): 
	 * 	`type.domain`, e.g. advert.good-loop.com
	 * 
	 * IF type is included in the service, it must always be used!
	 * Historically, we've used warts on the name for type -- this is deprecated.
	 */
	public final String service;
	
	/**
	 * 
	 * @param name Canonicalises via {@link IDoCanonical}
	 * @param kind Can be null
	 * @param service
	 * @param plugin
	 */
	public XId(String name, String service, IDoCanonical plugin) {
		if (plugin != null) name = plugin.canonical(name);
		this.name = name;
		this.service = service;
		// null@twitter is a real user :( c.f. bug #14109 
		assert notNullNameCheck() : "XID without a name?! '"+name+"' for "+service;		
		assert name != null;
		assert ! service.contains("@") : service;
	}

	static Map<String,IDoCanonical> canonicalForService = IDoCanonical.DUMMY_CANONICALISER;
	
	/**
	 * Use with {@link IDoCanonical#DUMMY_CANONICALISER} to allow XIds to be used _without_ initialising Creole.
	 * @param service2canonical
	 */
	public static void setService2canonical(
			Map<String, IDoCanonical> service2canonical) 
	{
		XId.canonicalForService = service2canonical;
	}

	/**
	 * @param name
	 * @param service
	 */
	public XId(String name, String service) {
		this(name, service, canonicalForService.get(service));
	}

	/**
	 * Usage: to bypass canonicalisation and syntax checks on name.
	 * This is handy where the plugin canonicalises for people, but XIds
	 * are used for both people and messages (e.g. Email).
	 *
	 * @param name
	 * @param service
	 * @param checkName Must be false to switch off the syntax checks performed by
	 * {@link #XId(String, String)}.
	 */
	public XId(String name, String service, boolean checkName) {
		this(name+"@"+service, checkName);
		assert ! service.contains("@") : "Bad service: "+service+" for "+this;
	}

	/**
	 * Convert a name@service String (as produced by this class) into
	 * a XId object.
	 * @param id e.g. "alice@twitter"
	 * @param kind e.g. KKind.Person
	 */
	public XId(String id) {
		int i = id.lastIndexOf('@');
		if (i <= 0) {
			throw new IllegalArgumentException("Invalid XId " + id);
		}
		this.service = id.substring(i+1);
		// Text for XStream badness
		assert ! id.startsWith("<xid>") : id;
		// HACK: canonicalise here for main service (helps with boot-strapping)
		if (isMainService()) {
			this.name = id.substring(0, i).toLowerCase();
			assert notNullNameCheck() : id;
			return;
		}
		// a database object?
		if (service.startsWith("DB")) {
//			try { // commented out to cut creole dependency
//				assert Fields.CLASS.fromString(service.substring(2)) != null : service;
//			} catch (ClassNotFoundException e) {
//				throw Utils.runtime(e);
//			}
			this.name = id.substring(0, i);
			assert notNullNameCheck() : id;
			return;
		}
		
		IDoCanonical plugin = canonicalForService.get(service);
		String _name = id.substring(0, i);
		// guard against an easy error
		assert ! _name.endsWith("@"+service) : "Bad XId "+id+" - duplicate service";
		this.name = plugin==null? _name : plugin.canonical(_name);
		assert notNullNameCheck() : id;
	}
	
	private boolean notNullNameCheck() {
		if (name==null || name.length()==0) return false;
		if (name.equals("null") && ! "twitter".equals(service)) return false;
		return true;
	}

	/**
	 * Convert a name@service String (as produced by this class) into
	 * a XId object. 
	 * This will tolerate badly formatted inputs! 
	 * @param canonicaliseName Must be false, to switch off using plugins to canonicalise
	 * the name.
	 */
	public XId(String id, boolean canonicaliseName) {
		assert ! canonicaliseName : "Wrong constructor! This one is for checkName=false "+this;
		int i = id.lastIndexOf('@');
		// handle unescaped web inputs -- with some log noise 'cos we don't want this
		if (i==-1 && id.contains("%40")) {
			Log.i("XId", "(handling smoothly) Unescaped url id: "+id);
			id = WebUtils2.urlDecode(id);
			i = id.lastIndexOf('@');
		}
		if (i==-1) {
			Log.e("xid.format", new Warning("No @ in XId: "+id));
			this.service="unknown";
			this.name=id;
		} else {
			this.service = id.substring(i+1);
			this.name = id.substring(0, i);
		}
		if ( ! notNullNameCheck()) {
			Log.e(service, "(ignoring) null name in XId: "+id);
		}
	}

	/**
	 * name@service
	 * This is the inverse of the String constructor, i.e.
	 * xid equals new Xid(xid.toString()). So you can use it for storage.
	 */
	@Override
	public String toString() {
		return name+"@"+service;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = name.hashCode();
		result = prime * result + service.hashCode();
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass()) {
			// Probably a bug!
//			Log.d("xid", "XId.equals() "+this+" to "+obj.getClass()+" "+ReflectionUtils.getSomeStack(8));
			return false;
		}
		XId other = (XId) obj;
		if (!name.equals(other.name))
			return false;
		if (!service.equals(other.service))
			return false;
		return true;
	}

	/**
	 * Never null
	 */
	public String getName() {
		return name;
	}

	public String getService() {
		return service;
	}
	
	/**
	 * TODO poke this value on JVM start-up
	 */
	static String MAIN_SERVICE = initMAIN_SERVICE();
	/**
	 * Special service for "Uses a local ID, so not a proper global XId"
	 */
	public static String LOCAL_SERVICE = "local"; 
	
	public boolean isMainService() {
		return MAIN_SERVICE.equals(service);
	}

	private static String initMAIN_SERVICE() {
		// NB: This property gets set by AWebsiteConfig
		String s = System.getProperty("XId.MAIN_SERVICE");
		if (s!=null) return s;
		// HACK -- known WW instances
		File dir = FileUtils.getWorkingDirectory();
		if (dir.getName().equals("creole")) {
			return "platypusinnovation.com";
		}			
		return "soda.sh";
	}

	/**
	 * @return true for rubbish XIds of the form "row-id@soda.sh" or "foo@temp"
	 */
	public boolean isTemporary() {
		return isService("temp") || (isMainService() && StrUtils.isNumber(name));
	}

	/**
	 * Convenience method
	 * @param other
	 * @return true if the services match
	 */
	public boolean isService(String _service) {
		return this.service.equals(_service);
	}

	/**
	 * Convenience for ensuring a List contains XId objects.
	 * @param xids May be Strings or XIds or IHasXIds (or a mix). Must not be null (but can contain nulls).
	 * Note: Strings are NOT run through canonicalisation -- they are assumed to be OK!
	 * @return a copy of xids, can be modified 
	 */
	public static ArrayList<XId> xids(Collection xids) {
		return xids(xids, false);
	}
	
	public static ArrayList<XId> xids(String[] xids) {
		return xids(Arrays.asList(xids));
	}
	
	/**
	 * Convenience for ensuring a List contains XId objects. Uses {@link #xid(Object, boolean)}
	 * @param xids May be Strings or XIds (or a mix). Can contain nulls
	 * @return a copy of xids, can be modified 
	 */
	public static ArrayList<XId> xids(Collection xids, boolean canonicalise) {
		final ArrayList _xids = new ArrayList(xids.size());
		for (Object x : xids) {
			if (x==null) continue;
			XId xid = xid(x, canonicalise);
			_xids.add(xid);
		}
		return _xids;
	}
	/**
	 * Flexible type coercion / constructor convenience.
	 * @param xid Can be String (actually any CharSequence) or XId or IHasXId or null (returns null). Does NOT canonicalise
	 * */
	public static XId xid(Object xid) {
		return xid(xid, false);
	}
	
	public static XId xid(Object xid, boolean canon) {
		if (xid==null) return null;
		if (xid instanceof XId) return (XId) xid;		
		if (xid instanceof CharSequence) {
			return new XId(xid.toString(), canon);
		}
		IHasXId hasxid = (IHasXId) xid;
		return hasxid.getXId();
	}
	
	@Override
	public String toJSONString() {
		return new SimpleJson().toJson(toString());
	}

	@Override
	public Object toJson2() throws UnsupportedOperationException {
		return toString();
	}

	@Override
	public int length() {
		return toString().length();
	}

	@Override
	public char charAt(int index) {
		return toString().charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return toString().subSequence(start, end);
	}

	@Override
	public int compareTo(XId o) {
		return toString().compareTo(o.toString());
	}

	/**
	 * @deprecated Warning - this can also return true for eg email
	 * @param xero
	 * @return true if input fits the XId id@service pattern
	 */
	public static boolean isa(String xero) {		
		return XID_PATTERN.matcher(xero).matches();
	}


	
}

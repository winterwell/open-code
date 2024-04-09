package com.winterwell.web.ajax;

import java.util.Collections;
import java.util.Map;

import com.winterwell.depot.IInit;
import com.winterwell.depot.INotSerializable;
import com.winterwell.gson.Gson;
import com.winterwell.utils.Dep;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.WrappedException;
import com.winterwell.utils.web.IHasJson;
import com.winterwell.utils.web.WebUtils2;

/**
 * Wrapper for json objects
 * 
 * TODO should this recognise IHasJson??
 * 
 * @author daniel
 *
 */
public class JThing<T> 
implements INotSerializable, IHasJson // serialize the json not this wrapper 
{

	private String json;
	private Map<String,Object> map;
	private T java;
	private Class<T> type;
	
	/**
	 * Optionally used for ES version control
	 */
	public Object version;
	
	/**
	 * Equivalent to new JThing().setJava(item)
	 * @param item the Java POJO
	 */
	public JThing(T item) {
		setJava(item);
	}
	
	/**
	 * Usually needed (depending on the gson setup) for {@link #java()} to deserialise json.
	 * Note: Once set, you cannot change the type (repeated calls with the same type are fine).
	 * @param type
	 * @return this
	 */
	public JThing<T> setType(Class<T> type) {
		if (this.type == type) return this;
		// Once set, you cannot change the type (repeated calls with the same type are fine).
		assert this.type==null || this.type.equals(type) : this.type+" != "+type;
		this.type = type;
		assert java==null || type==null || ReflectionUtils.isa(java.getClass(), type) : type+" vs "+java.getClass();
		return this;
	}
	
	/**
	 * What class of thing is this?
	 * @return can be null if type info hasn't been set
	 */
	public Class<T> getType() {
		return type;
	}
	
	public JThing() {		
	}
	
	public JThing(String json) {
		this.json = json;
	}

	/**
	 * @return The JSON string.
	 * <br>
	 * NB: This is NOT the same as {@link #toString()}, which returns a shorter snippet.
	 */
	public String string() {
		if (json==null && map!=null) {
			Gson gson = gson();
			json = gson.toJson(map);
		}
		if (json==null && java!=null) {
			Gson gson = gson();
			json = gson.toJson(java);
		}
		return json;
	}
	
	@Override
	public String toJSONString() {
		return string();
	}
	
	private Gson gson() {
		if (Dep.has(Gson.class)) {
			return Dep.get(Gson.class);
		}
		// a default
		return new Gson();
	}

	/**
	 * @return An unmodifiable map view. Can be null if the value is null
	 * This is unmodifiable for protection against careless edits. 
	 * If you do want to edit this, do a new HashMap() copy.
	 * @see #put(String, Object)
	 */
	public Map<String, Object> map() {
		if (map==null && string()!=null) {
			map = WebUtils2.parseJSON(json);
		}
		if (map==null) {
			return null;
		}
		return Collections.unmodifiableMap(map);
	}
	
	public JThing<T> setJava(T java) {
		this.java = java;
		if (java==null) return this;
		map = null;
		json = null;		
		if (type==null) type = (Class<T>) java.getClass();
		assert ReflectionUtils.isa(java.getClass(), type) : type+" vs "+java.getClass();		
		return this;
	}
	
	public JThing<T> setJson(String json) {
		this.json = json;
		this.java = null;
		this.map = null;
		return this;
	}
	
	public T java() {
		if (java!=null) return java;
		// convert from json?
		String sjson = string();
		if (sjson == null) {
			return null; // nope, its really null
		}
		assert type != null : "Call setType() first "+this;
		try {
			Gson gson = gson();			
			T pojo = gson.fromJson(sjson, type);
			// init?
			if (pojo instanceof IInit) {
				((IInit) pojo).init();
			}
			// this will null out the json/map
			// ...which is good, as extra json from the front-end can cause bugs with ES mappings.
			setJava(pojo);		
			return java;
		} catch (Throwable ex) {
			// add in extra info
			throw new WrappedException(ex+" Cause POJO: "+StrUtils.ellipsize(sjson, 200), ex);
//			less info = less noise here +" "+Containers.subList(Arrays.asList(ex.getStackTrace()), 0, 8)
		}
	}
	
	/**
	 * Equivalent to {@link #java}
	 * @return
	 */
	public final T get() {
		return java();
	}
	
	/**
	 * NOT the json - this is a short version for debug / logging.
	 * @see #string()
	 */
	@Override
	public String toString() {	
		return "JThing"+StrUtils.ellipsize(string(), 100)+"";
	}
	/**
	 * Modify the map() view, and null (i.e. force an update) of the string() view + null the java() view
	 * @param k
	 * @param v
	 */
	public void put(String k, Object v) {
		map();
		map.put(k, v);
		java = null;
		json = null;
	}
	public JThing<T> setMap(Map<String, Object> obj) {
		this.map = obj;
		java = null;
		json = null;
		return this;
	}

	public Object toJson2() {
		// is it an object? (the normal case)
		if (map!=null) return map();
		if (json!=null && json.startsWith("{")) {
			return map();
		}
		if (string()==null) return null;
		return WebUtils2.parseJSON(string());		
	}

	/**
	 * @param data A json object (from a parse). Normally a Map, but it could be an array/list or a primitive.
	 * @return this
	 */
	public JThing<T> setJsonObject(Object _data) {
		// no unnecessary seialisation work for maps
		if (_data instanceof Map) {
			setMap((Map) _data);
			return this;
		}
		// play it safe -- set as a json string
		String djson = WebUtils2.generateJSON(_data);
		setJson(djson);
		return this;
	}

	/**
	 * @param klass
	 * @return true if the thing (which might be null) is an instance of klass
	 */
	public boolean isa(Class klass) {
		if (ReflectionUtils.isa(getType(), klass)) {
			return true;
		}
		// check the object itself
		return java() != null && ReflectionUtils.isa(java().getClass(), klass);		
	}
	
}

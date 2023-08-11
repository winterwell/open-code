package com.winterwell.web.ajax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.IProperties;
import com.winterwell.utils.Key;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.ajax.AjaxMsg.KNoteType;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.fields.SafeString;

/** 
 *  
 * Use with
 * {@link WebUtils2#sendJson(JsonResponse, javax.servlet.http.HttpServletResponse)}.
 * Typical output:
 * <code>
 * {"success":"true", "cargo":{}, "errors":[], "messages":[]}
 * </code>
 * 
 * @deprecated replace this with JSend
 * @author daniel
 * 
 */
public class JsonResponse implements IProperties {

	/**
	 * If true, also include jsend info in {@link #toJSON()}
	 */
	public static boolean INCLUDE_JSEND;
	
	/**
	 * Specifies a function to be used for a jsonp response. NB: not actively
	 * used within SoDash, but it will be used in the external API.
	 */
	public static final SafeString CALLBACK = new SafeString("callback");

	/**
	 * Holds the main data from the servlet call.
	 */
	public static final Key<Object> JSON_CARGO = new Key<Object>("cargo");

	/**
	 * Array of errors (json object or html) to report to the user.
	 */
	@Deprecated
	public static final Key<List> JSON_ERRORS = new Key<List>("errors");

	/**
	 * Array of messages to display. Either use Strings, or "rich" notifications.
	 * @see Notifications.js for the format
	 */
	public static final Key<List> JSON_MESSAGES = new Key<List>(
			"messages");

	/**
	 * true if all is good, false for errors
	 */
	public static final Key<Boolean> JSON_SUCCESS = new Key<Boolean>("success");

	public String callback;

	private final Map properties = new HashMap();

	private String cargoJson;


	/**
	 * A blank response.
	 */
	public JsonResponse() {
	}

	/**
	 * Build a response from the state, using {@link #CALLBACK},
	 * {@link WebRequest#popMessages()}, and
	 * {@link WebRequest#popExceptionMessages()}
	 * 
	 * @param state
	 * @param cargo
	 *            Can be null
	 */
	public JsonResponse(WebRequest state, Object cargo) {
		this.callback = state.get(CALLBACK);		
		setCargo(cargo);		
		List<AjaxMsg> msgs = state.popMessages();
		// is there an exception?		
		if (msgs==null) {
			setSuccess(true);
			return;
		}
		List exs = new ArrayList(1);
		List jmsgs = new ArrayList(msgs.size());
		for (AjaxMsg msg : msgs) {
			if (msg==null) continue; // WTF? Defence against a rare bug #5693
			Object jo = msg.toJsonObj();
			jmsgs.add(jo);
			if (msg.type == KNoteType.error) {
				exs.add(jo);
			}
		}
		setSuccess(exs.isEmpty());		
		properties.put(JSON_MESSAGES, jmsgs);
		properties.put(JSON_ERRORS, exs);			
	}

	
	public JsonResponse(WebRequest state) {
		this(state, null);
	}

	/**
	 * If you've encoded the object into json yourself, use {@link #setCargoJson(String)} instead. 
	 * @param cargo 
	 */
	public void setCargo(Object cargo) {
		properties.put(JSON_CARGO, cargo);
	}
	
	public Object getCargo() {
		return properties.get(JSON_CARGO);
	}

	@Override
	public <T> boolean containsKey(Key<T> key) {
		return get(key) != null;
	}

	@Override
	public <T> T get(Key<T> key) {
		Object v = properties.get(key);
		// if (v==null) v = defaultProperties.get(key);
		return (T) v;
	}

	@Override
	public Collection getKeys() {
		return properties.keySet();
	}

	@Override
	public boolean isTrue(Key<Boolean> key) {
		Boolean v = get(key);
		return v != null && v;
	}

	@Override
	public <T> T put(Key<T> key, T value) {
		if (value == null)
			return (T) properties.remove(key);
		else
			return (T) properties.put(key, value);
	}
	

	public <T> T put(String key, T value) {
		return (T) put(new Key(key), value);
	}

	/**
	 * This is NOT the json to output. That is made using Containers.getMap()
	 */
	@Override
	public String toString() {
		return "JsonResponse[" + properties + "]";
	}
	
	public String toJSON() {
		// convert properties
		Map<String, Object> map = Containers.getMap(this);
		
		if (INCLUDE_JSEND) {
			Boolean success = get(JSON_SUCCESS);
			map.put("status", Utils.yes(success)? "success" : "error");
			List msgs = get(JSON_MESSAGES);
			if ( ! Utils.isEmpty(msgs)) {
				map.put("message", msgs.get(0).toString());
			}
			if (cargoJson==null) {
				map.put("data", getCargo());
			}
		}
		
		String json = WebUtils2.generateJSON(map);
		if (cargoJson==null) {
			return json;
		}
		// HACK direct json cargo + the json for the response wrapper
		String json2 = "{\"cargo\":"+cargoJson+","+json.substring(1);
		return json2;
	}

	public void setSuccess(boolean b) {
		properties.put(JSON_SUCCESS, b);
	}

	@Deprecated // AjaxMsg is preferred to raw Strings
	public void setMessage(String msg) {
		properties.put(JSON_MESSAGES, Arrays.asList(msg));
	}

	public JsonResponse setCargoJson(String json) {
		assert properties.get(JSON_CARGO) == null : this;
		cargoJson = json;
		return this;
	}

}

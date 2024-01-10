package com.winterwell.nlp.dict;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.winterwell.utils.containers.AbstractMap2;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;

/**
 * Wrap a map, and allow for lenient matching on keys. E.g. for ingesting csv files.
 * Uses {@link NameMapper}
 * @author daniel
 *
 * @param <X>
 * @testedby {@link FlexibleKeyMapTest}
 */
public class FlexibleKeyMap<X> extends AbstractMap2<String,X> {

	private Map<String, X> base;
	private NameMapper nameMapper;

	public FlexibleKeyMap(Map<String,X> base) {
		this.base = base;
		nameMapper = new NameMapper(base.keySet());
	}
	
	@Override
	public Set<String> keySet() throws UnsupportedOperationException {
		return base.keySet();
	}
	
	@Override
	public Set<Entry<String, X>> entrySet() {
		return base.entrySet();
	}
	
	@Override
	public X get(Object key) {
		String k = getOurKey((String) key);
		return base.get(k);
	}

	private String getOurKey(String key) {
		List<String> k = nameMapper.getAmbiguous(key);
		if (k==null || k.isEmpty()) {
			return null;
		}
		if (k.size() > 1) {
			Log.w("FlexibleKeyMap", "ambiguous key, using 1st: "+key+" -> "+k);
		}
		return Containers.first(k);
	}

	/**
	 * <p>This can change the key to use an existing similar key!</p>
	 * 
	 * {@inheritDoc}
	 */
	@Override
	public X put(String key, X value) {
		String k = getOurKey((String) key);
		return base.put(k, value);
	}


}

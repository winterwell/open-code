package com.winterwell.utils.web;

import java.util.Map;

public interface ISerializeJson {

	<X> X fromJson(String json, Class<X> klass);

	String toJson(Object source);

	<T> T fromJson(String json);
	
	default <X> X convert(Map mapFromJson, Class<X> klass) {
		// inefficient, but should work
		String json = toJson(mapFromJson);
		X obj = fromJson(json, klass);
		return obj;
	}

}

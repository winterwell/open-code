package com.winterwell.web.app;

import java.util.List;
import java.util.Map;

public class CrudSearchResults<T> {

	List<T> hits;
	
	Integer total;
	
	Object next;

	public List<T> getHits() {
		return hits;
	}
	
	public int getTotal() {
		return total==null? (hits==null? 0 : hits.size()) : total; // NB: slightly paranoid code
	}

	@Override
	public String toString() {
		return "CrudSearchResults[total="+total+"]";
	}

	public Object getNext() {
		return next;
	}
	
}

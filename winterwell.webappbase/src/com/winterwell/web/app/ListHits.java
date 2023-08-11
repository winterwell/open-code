package com.winterwell.web.app;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.winterwell.es.client.ESHit;

public class ListHits<T> 
// implements SearchResponse // based on
{

	List<T> hits;

	public List<T> getHits() {
		return hits;
	}


	public long getTotal() {
		return total;
	}

	public List<Map<String, Object>> getSearchResults() {
		// TODO Auto-generated method stub
		return null;
	}

	public <X> List<X> getSearchResults(Class<? extends X> klass) {
		// TODO Auto-generated method stub
		return null;
	}

	public <X> List<ESHit<X>> getHits(Class<? extends X> type) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String toString() {
		return "ListHits[hits=" + hits + "]";
	}

	long total;

	@Override
	public int hashCode() {
		return Objects.hash(hits);
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ListHits other = (ListHits) obj;
		return Objects.equals(hits, other.hits);
	}
	
}

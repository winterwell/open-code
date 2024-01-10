package com.winterwell.nlp.dict;

import org.junit.Test;

import com.winterwell.utils.containers.ArrayMap;

public class FlexibleKeyMapTest {

	@Test
	public void testGetPut() {
		ArrayMap base = new ArrayMap("First Name", "Dan", "Name", "Daniel Winterstein");
		FlexibleKeyMap<String> fkm = new FlexibleKeyMap<>(base);
		assert fkm.get("Name").equals("Daniel Winterstein");
		assert fkm.get("name").equals("Daniel Winterstein");
		assert fkm.get("first name").equals("Dan");
		assert fkm.get("first").equals("Dan");
		
		fkm.put("name", "Danny");
		assert fkm.get("Name").equals("Danny");
		assert ! fkm.keySet().contains("name");
		
	}

}

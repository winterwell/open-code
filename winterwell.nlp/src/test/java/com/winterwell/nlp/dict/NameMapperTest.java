package com.winterwell.nlp.dict;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class NameMapperTest {

	@Test
	public void testNameMapperCollection() {
		NameMapper nm = new NameMapper(Arrays.asList("Alice Smith", "Ben Jones", "Alice Jones"));
		List<String> u = nm.addTheirNames(Arrays.asList("Alice","Ben"));
		assert u.contains("Alice") : u;
		assert u.size() == 1;
		List<String> a = nm.getAmbiguous("Alice");
		System.out.println(a);
		assert a.size() == 2;
		Map<String, String> map = nm.getOurNames4TheirNames();
		assert map.get("Ben").equals("Ben Jones");
		assert ! map.containsKey("Alice");
	}

}

package com.winterwell.datalog;

import org.junit.Test;

public class BreakdownTest {

	@Test
	public void testToString() {
		Breakdown b = Breakdown.fromString("foo/bar{\"c\":\"sum\"}");
		System.out.println(b);
		Breakdown b2 = Breakdown.fromString(b.toString());
		assert b.toString().equals(b2.toString());
		assert b.equals(b2);
	}

}

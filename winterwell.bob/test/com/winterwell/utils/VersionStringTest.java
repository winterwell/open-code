package com.winterwell.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class VersionStringTest {

	@Test
	public void testGeqVersionString() {
		VersionString v102 = new VersionString("1.0.2");
		VersionString v110 = new VersionString("1.1.0");
		VersionString v110b = new VersionString("1.1.0 bah");
		assert ! v102.geq(v110);
		assert v110.geq(v102);
		assert v110.geq(v110b);
		assert v110b.geq(v110);
	}


	@Test
	public void testGeqLengthMismatch() {
		VersionString v102 = new VersionString("1.0.2");
		VersionString v110 = new VersionString("1.1");
		VersionString v110b = new VersionString("1.1 bah");
		assert ! v102.geq(v110);
		assert v110.geq(v102);
		assert v110.geq(v110b);
		assert v110b.geq(v110);
		
		assert v110b.geq(v102);
		assert ! v102.geq(v110b);
	}

	@Test
	public void testIsHigher() {
		VersionString v102 = new VersionString("1.0.2");
		VersionString v110 = new VersionString("1.1.0");
		assert ! v102.isHigher(v110);
		assert v110.isHigher(v102);
	}

}

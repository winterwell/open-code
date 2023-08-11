package com.winterwell.web.app;

import org.junit.Test;

import com.winterwell.utils.threads.ICallable;
import com.winterwell.utils.time.Time;

public class CommonFieldsTest {

	@Test
	public void testGetPeriod() {
		ICallable<Time> e = CommonFields.END.fromString("2023-01-31");
		Time end = e.call();
		assert end.equals(new Time(2023,2,1)) : end.toISOString();
	}

}

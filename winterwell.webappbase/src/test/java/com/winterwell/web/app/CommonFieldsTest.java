package com.winterwell.web.app;

import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.threads.ICallable;
import com.winterwell.utils.time.Time;

public class CommonFieldsTest {

	@Test @Ignore // maven version bleurgh -- should pass
	public void testGetPeriod() {
		ICallable<Time> e = CommonFields.END.fromString("2023-01-31");
		Time end = e.call();
		assert end.equals(new Time(2023,2,1)) : end.toISOString();
	}

}

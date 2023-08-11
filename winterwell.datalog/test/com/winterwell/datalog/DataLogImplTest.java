package com.winterwell.datalog;

import org.junit.Test;

import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class DataLogImplTest {

	@Test
	public void testGetBucket2() {
		{
			//  24 Oct 2022 18:30 to 24 Oct 2022 18:45 vs 
			// Mon Oct 24 18:20:43 GMT 2022 from Tue Oct 25 18:15:00 GMT 2022 15 minutes
			Time time = new Time(2022,10,24,18,20,43);
			Time start = new Time(2022,10,25,18,15,0);
			Dt dt = new Dt(15,TUnit.MINUTE);
			Period b = DataLogImpl.getBucket2(time, start, dt);
			System.out.println(b);
		}
	}

}

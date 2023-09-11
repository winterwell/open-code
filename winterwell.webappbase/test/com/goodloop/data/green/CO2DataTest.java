package com.goodloop.data.green;

import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.Test;

import com.goodloop.data.KAdFormat;
import com.goodloop.data.green.CO2Data;
import com.goodloop.data.green.CO2Data.CO2;
import com.goodloop.data.green.CO2Data.DomainSurvey;
import com.winterwell.utils.Printer;
import com.winterwell.utils.time.Time;

public class CO2DataTest {

	@Test
	public void testGetCo2() {
		String pub = "nytimes.com";
		CO2 co2 = CO2Data.getCo2(KAdFormat.display, "GB", null, new Time(), 2, false, 0.2, 1);
		System.out.println(co2);
		Printer.out("CO2 kg per million: "+co2.getTotal()*1000000); // about a ton
		Printer.out("Supply path for display: "+Printer.str(100*co2.supplyPath()/co2.getTotal())+"%");
		CO2 co2v = CO2Data.getCo2(KAdFormat.video, "GB", null, new Time(), 2, false, 2, 1);
		assert co2v.getTotal() > co2.getTotal() : co2v; // video should be bigger
	}
	
}

package com.winterwell.datalog;

import java.util.Map;

import org.junit.Test;

import com.winterwell.maths.stats.distributions.d1.MeanVar1D;
import com.winterwell.utils.Printer;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.web.XStreamUtils;

public class DataLogEventTest {

	@Test
	public void testToJson2_parsingProblem() {
		DataLogEvent de = new DataLogEvent("test", 2, "testTag", new ArrayMap(
				"w", "foo",
				"width", "bar",
				"h", "17"));
		Map<String, ?> json = de.toJson2();
		Printer.out(json);
		assert ((Number)json.get("h")).doubleValue() == 17;
	}

	@Test
	public void testToJson2_simple() {
		DataLogEvent de = new DataLogEvent("testTag", 2);
		Map<String, ?> json = de.toJson2();
		System.out.println(json);
	}

	@Test
	public void testToJson2_meanVar() {
		ESStorage ess = new ESStorage();
		MeanVar1D mv = new MeanVar1D();
		mv.train1(1.0);
		mv.train1(2.0);
		mv.train1(3.0);
		DataLogEvent de = ess.event4distro("testDistro", mv);
		Map<String, ?> json = de.toJson2();
		// check it has the extra distro info in it
		Object xtra = de.getProp("xtra");
		assert xtra != null;		
//		assert json.containsKey("xtra") : json;
//		String xtra = (String) json.get("xtra");
//		assert xtra.contains("mean");		
		String xml = (String) ((Map) xtra).get("xml");
		MeanVar1D obj = XStreamUtils.serialiseFromXml(xml);
		System.out.println(obj);
		assert obj.getMean() == mv.getMean() : obj;
	}
}

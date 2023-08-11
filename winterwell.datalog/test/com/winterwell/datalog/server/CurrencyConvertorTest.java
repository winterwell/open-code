package com.winterwell.datalog.server;

import java.io.IOException;

import org.junit.Test;

import com.goodloop.data.KCurrency;
import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogHttpClient;
import com.winterwell.datalog.Dataspace;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class CurrencyConvertorTest {
	
	@Test
	public void testFetchSaveLoad() throws IOException {
		init();

		Time now = new Time();
		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, now);
		DataLogEvent e = cc.fetchCurrRate();
		Printer.out(e);
		Utils.sleep(1500);
		
		DataLogEvent e2 = cc.loadCurrDataFromES(now);
		assert e != null;
		assert e2 != null;
		System.out.println(e);
		System.out.println(e.getProp("GBP2USD"));
		System.out.println(e2);
		System.out.println(e2.getProp("GBP2USD"));
		assert e.getProp("GBP2USD").equals(e2.getProp("GBP2USD")) : e+" vs "+e2;
	}



	@Test
	public void testUSD_AUD() throws IOException {
		init();

		Time now = new Time(2023,05,01);
		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.USD, KCurrency.AUD, now);
		double aud = cc.convertES(12);
		Printer.out(aud);
	}


	@Test
	public void testFuture() throws IOException {
		init();

		Time now = new Time().plus(3, TUnit.MONTH);
		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, now);
		double v = cc.convertES(1);
		Printer.out(v);
	}
	
	@Test
	public void testConvertCAD2GBP() throws IOException {
		init();

		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.CAD, KCurrency.GBP, new Time());
		DataLogEvent e = cc.fetchCurrRate();
		Printer.out(e);
		Utils.sleep(1500);
		double gbp = cc.convertES(1);
		assert MathUtils.approx(gbp, 1/1.58);
	}
	
	@Test
	public void testConvertJPY2GBP() throws IOException {
		init();

		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.JPY, KCurrency.GBP, new Time());
		DataLogEvent e = cc.fetchCurrRate();
		Printer.out(e);
		Utils.sleep(1500);
		double gbp = cc.convertES(1);
		assert MathUtils.approx(gbp, 1/163.75);
	}

	@Test
	public void testConvertCHF() throws IOException {
		init();

		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.CHF, KCurrency.GBP, new Time());
		DataLogEvent e = cc.fetchCurrRate();
		Printer.out(e);
		Utils.sleep(1500);
		double gbp = cc.convertES(1);
		assert MathUtils.approx(gbp, 1/1.15);
	}
	
	@Test
	public void testLoad() throws IOException {
		init();

		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, new Time());
		
		DataLogEvent e2 = cc.loadCurrDataFromES(new Time().minus(1, TUnit.DAY));
		System.out.println("e2:"+e2);
		assert e2 != null;
	}
	
	@Test
	public void testConvert() throws IOException {
		init();
		
		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, new Time());
		double usd = cc.convertES(0.05);
		System.out.println(usd);
	}
	
	@Test
	public void testConvertHardCoded() throws IOException {
		init();
		
		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.CAD, new Time());
		double usd = cc.convert2_hardCoded(10);
		System.out.println(usd);
		assert usd > 11;
		
		CurrencyConvertor cc2 = new CurrencyConvertor(KCurrency.CAD, KCurrency.GBP, new Time());
		double gbp = cc2.convert2_hardCoded(10);
		System.out.println(gbp);
		assert gbp < 9;
	}
	
	public void testLoadwithCache() throws IOException {
		init();
		Time today = new Time();
		CurrencyConvertor cc = new CurrencyConvertor(KCurrency.GBP, KCurrency.USD, today);
		DataLogEvent rate = cc.loadCurrDataFromES(today);
		if (rate == null) {
			rate = cc.fetchCurrRate();
		}
		cc.cache.put(today.toISOStringDateOnly(), rate);
		
		DataLogEvent whatsInCache = cc.cache.get(today.toISOStringDateOnly());
		System.out.println(whatsInCache);
	}
	
	private void init() {
//		DataLogConfig dlc = ConfigFactory.get().getConfig(DataLogConfig.class);
		
		DataLogConfig dlc = new DataLogConfig();
		Dep.set(DataLogConfig.class, dlc);
	}

	public static Double currConvert(Object curr, String amount) {
		
		// TODO Fetch event currRate from ES with the latest date
		DataLogHttpClient d = new DataLogHttpClient(new Dataspace("fx"));
		SearchQuery sq = new SearchQuery("evt:currrate");
		System.out.println(d.getEvents(sq , 1));
		
		Double currRate = 1.00;
		if (curr == "GBP") {
			currRate = 1.34;
		} else if (curr == "EUR") {
			currRate = 1.15;
		}
		Double amountusd =  Double.valueOf(curr.toString()).doubleValue() * currRate;
		
		return amountusd;
	}
}

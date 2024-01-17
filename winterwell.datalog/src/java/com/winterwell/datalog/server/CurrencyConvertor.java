package com.winterwell.datalog.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.goodloop.data.KCurrency;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.DataLogHttpClient;
import com.winterwell.datalog.DataLogRemoteStorage;
import com.winterwell.datalog.Dataspace;
import com.winterwell.json.JSONObject;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Cache;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;

/**
 * Uses a static cache, so individual objects are cheap to make
 * FIXME load and stash data
 * @author Wing, daniel
 * @testedby {@link CurrencyConvertorTest}
 */
public class CurrencyConvertor {
	
	private KCurrency from;
	private KCurrency to;
	private Time date;

	/**
	 * 
	 * @param from
	 * @param to Can match from (in which case convertES() will just return the input)
	 * @param date
	 */
	public CurrencyConvertor(KCurrency from, KCurrency to, Time date) {
		this.from = from;
		this.to = to;
		this.date = date;
	}

	@Override
	public String toString() {
		return "CurrencyConvertor[from=" + from + ", to=" + to + ", date=" + date + "]";
	}

	/**
	 * Old conversion class, hard coded currency rate
	 * @param cidDntn
	 * @return
	 */
	double convert2_hardCoded(double cidDntn) {
		if (from==to) { // no-op
			return cidDntn;
		}
		String currencyConversion = from + "_" + to;
		Double conversionVal = CURRENCY_CONVERSION.get(currencyConversion);
		if (conversionVal==null) {
			// inverse?
			String invcurrencyConversion = to + "_" + from;
			Double invconversionVal = CURRENCY_CONVERSION.get(invcurrencyConversion);	
			if (invconversionVal!=null) {
				conversionVal = 1/invconversionVal;
			} else {
				// HACk route via GBP? TODO ??promote this to earlier in the flow, above hardcoded
				if (from!=KCurrency.GBP && to!=KCurrency.GBP) {
					CurrencyConvertor a = new CurrencyConvertor(from, KCurrency.GBP, date);
					CurrencyConvertor b = new CurrencyConvertor(KCurrency.GBP, to, date);
					double gbp = a.convertES(cidDntn);
					double bval = b.convertES(gbp);
					return bval;
				}
				throw new TodoException("Setup currency conversion for "+currencyConversion);
			}
		}
		return cidDntn*conversionVal;
	}
	
	/**
	 * HACK - estimate conversions to handle adding conflicting currencies
	 * Sourced from https://www.x-rates.com/table/?from=GBP&amount=1 Sep 20, 2021 16:43
	 * EUROS sourced from https://www.google.com/search?q=euro+to+pound&oq=euro+to+pound Sep 20, 2021 16:46
	 */
	static final Map<String,Double> CURRENCY_CONVERSION = new ArrayMap(
		"GBP_USD", 1.2,
		"GBP_AUD", 1.885,
		"GBP_NZD", 2.0358665, // https://www.xe.com/currencyconverter/convert/?Amount=1&From=GBP&To=NZD Apr 26 2023
		"GBP_EUR", 1.16,
		"GBP_CAD", 1.58,
		"GBP_CHF", 1.15, // Google XE 5 Dec 2022
		"GBP_SGD", 1.54, // Yahoo, Sep 27th 2022
		"GBP_TRY", 24.228071, // 26 Apr 2023 https://www.xe.com/currencyconverter/convert/?Amount=1&From=GBP&To=TRY
		"GBP_JPY", 163.50, // Google XE 2 Mar 2023
		"USD_AUD", 1.380,
		"USD_EUR", 0.85,
		"EUR_AUD", 1.62,
		"ZAR_USD", 0.06 // Google XE 17 Nov 2022
	);
	
	// Turns out do not need this part
//	public Time latestDateWithRate(Time latestDate) throws IOException {
//		Time today = new Time();
//		if (loadCurrDataFromES(latestDate) == null) {
//			if (latestDate.toISOStringDateOnly().equalsIgnoreCase(today.toISOStringDateOnly())) {
//				fetchCurrRate();
//				System.out.println("Fetching Today's rate and write into ES.");
//				Utils.sleep(1500);
//				latestDateWithRate(latestDate.minus(1, TUnit.DAY));
//			} else if (latestDate.toISOStringDateOnly().equalsIgnoreCase(today.minus(1, TUnit.DAY).toISOStringDateOnly())) {
//				latestDateWithRate(latestDate.minus(1, TUnit.DAY));
//			} else if (latestDate.toISOStringDateOnly().equalsIgnoreCase(today.minus(2, TUnit.DAY).toISOStringDateOnly())) {
//				return today;
//			}
//		}
//		System.out.println("Reading currency rate of "+latestDate.toISOStringDateOnly());
//		return latestDate;
//	}
	
	static final Cache<String, DataLogEvent> cache = new Cache(100);
	private static final String LOGTAG = "CurrencyConvertor";
	
	/**
	 * New conversion class, fetch currency rate in ES. Uses a cache so it is fast once warmed up.
	 * @param amount
	 * @return
	 * @throws IOException 
	 */
	public double convertES(double amount) {		
		if (amount==0) { // fast zero
			return 0;
		}
		if (from==to) { // eg GBP to GBP -- a no-op
			return amount;
		}
		if (date.isAfter(new Time())) {
			// the future - clip to now
			date = new Time();
		}
		DataLogEvent rateFromCache = cache.get(date.toISOStringDateOnly());
		if (rateFromCache == null) {
			DataLogEvent rate = loadCurrDataFromES(date);
			if (rate == null) {
				// looking for today's rate?
				if (date.toISOStringDateOnly().equals(new Time().toISOStringDateOnly())) {
					try {
						rate = fetchCurrRate();
					} catch(Exception ex) {
						Log.e(LOGTAG, ex);
					}
					if (rate == null) {
						Log.d("CurrencyConvertor", "fetchCurrRate failed for "+date+" - using hardcoded FX");
						return convert2_hardCoded(amount);
					}
				} else {
					// fail :(
					Log.d("CurrencyConvertor", "No data for "+date+" - using hardcoded FX");
					return convert2_hardCoded(amount);
				}
			}
			cache.put(date.toISOStringDateOnly(), rate);
			rateFromCache = rate;
		} // ./rateFromCache		
		
		double conversionVal = convertES2_conversionRate(rateFromCache, from, to);
		if (conversionVal==0) {
			// fallback
			return convert2_hardCoded(amount);
		}
		return amount*conversionVal;
	}

	private double convertES2_conversionRate(DataLogEvent rateFromCache, KCurrency _from, KCurrency _to) {
		String fromXtoY = _from + "2" + _to;
		// Direct?
		double conversionVal = MathUtils.toNum(rateFromCache.getProp(fromXtoY));		
		if (conversionVal!=0) {
			return conversionVal;
		}
		// inverse?
		String invcurrencyConversion = _to + "2" + _from;
		double invconversionVal = MathUtils.toNum(rateFromCache.getProp(invcurrencyConversion));	
		if (invconversionVal!=0) {
			conversionVal = 1/invconversionVal;		
			return conversionVal;
		}
		// via USD
		if (_to != KCurrency.USD && _from!=KCurrency.USD) {
			double from2USD = convertES2_conversionRate(rateFromCache, _from, KCurrency.USD);
			double USD2to = convertES2_conversionRate(rateFromCache, KCurrency.USD,_to);
			if (from2USD!=0 && USD2to!=0) {
				conversionVal = from2USD*USD2to;
				return conversionVal;
			}
		}
		Log.d("CurrencyConvertor", "No data for currency:"+_from+" date: " +date+" - using hardcoded FX");
		return 0;
	}
	
	private static final String currrate = "currrate";


	/**
	 * Fetch latest exchange rates from an API and save to central DataLog.
	 * Currently uses exchangerate.host: https://exchangerate.host/documentation
	 * @return A DataLogEvent containing a map of currencies
	 * @throws IOException
	 */
	public DataLogEvent fetchCurrRate() throws IOException {
		LoginDetails ld = Logins.get("exchangerate.host");
		assert ld != null && ld.apiKey != null : "No exchangerate.host API key found by Logins.java";
		// NB DO NOT UPGRADE TO HTTPS: exchangerate.host free tier only permits HTTP access.
		String resBody = new FakeBrowser().getPage("http://api.exchangerate.host/live&source=USD&access_key=" + ld.apiKey);

		Map<String, ?> quotes = new JSONObject(resBody).getJSONObject("quotes").getMap();
		Map<String, Double> rateMap = new HashMap<String, Double>();

		// Previous code just pulled out currencies with an entry in KCurrency.java
		// exchangerate.host gives us a large variety by default - no reason not to store all of them
		for (Entry<String, ?> usd2x : quotes.entrySet()) {
			// Quotes object uses key pattern "USDGBP", "USDAUD" etc
			String toCurrencyCode = usd2x.getKey().substring(3);
			// How many XXX does one USD buy?
			double toCurrencyValue = MathUtils.toNum(usd2x.getValue());
			if (toCurrencyValue == 0) continue; // very funny
			// By convention we store the inverse - how many USD does one XXX buy?
			rateMap.put(toCurrencyCode + "2USD", 1 / toCurrencyValue);
		}
		
		DataLogEvent event = new DataLogEvent("fx", 1, currrate, rateMap);
		DataLogRemoteStorage.saveToRemoteServer(event);
		return event;
	}
	

	DataLogEvent loadCurrDataFromES(Time loadingDate) {
		DataLogHttpClient dlc = new DataLogHttpClient(new Dataspace("fx"));
		Time startDay = TimeUtils.getStartOfDay(loadingDate);
		Time endDay = TimeUtils.getEndOfDay(loadingDate);
		dlc.setPeriod(startDay, endDay);
		dlc.initAuth("good-loop.com");
		SearchQuery sq = new SearchQuery("evt:"+currrate);
		dlc.setDebug(true);
		// get a few (should just be 1)
		List<DataLogEvent> rate = dlc.getEvents(sq , 5);		
		if (rate.size() == 0) {
			return null;
		}
		// ??pick the closest to time? But we should only store one per day
		return rate.get(0);
	}
	

}

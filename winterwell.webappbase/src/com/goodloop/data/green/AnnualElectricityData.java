package com.goodloop.data.green;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import com.winterwell.jgeoplanet.ISO3166;
import com.winterwell.utils.Printer;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.io.CSVSpec;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.web.FakeBrowser;

/**
 * From OurWorldInData
 * https://ourworldindata.org/grapher/carbon-intensity-electricity
 * 
 * @author daniel
 *
 */
public class AnnualElectricityData implements IElectricityData {

	/**
	 * Downloaded and uploaded into g-drive
	 */
	static final String url = "https://docs.google.com/spreadsheets/d/1vglWPxe-RrEIVDBF_5KbD8EULIoh2Zgz0uFeJwF-yoM";
	private static final String LOGTAG = "AnnualElectricityData";
	private ISO3166 iso;
	private Map<String,Double> co2ForCountry = new HashMap();
	
	public AnnualElectricityData() {
		init();
	}
	
	private void init() {
		String gurl = 
//			"https://docs.google.com/spreadsheets/d/"+docId+"/gviz/tq?tqx=out:csv";
			url+"/export?format=csv&gid=2131813852";
		CSVReader csv = new CSVReader(new StringReader(new FakeBrowser().getPage(gurl)),
			new CSVSpec());
		csv.setNumFields(-1); // allow e.g. notes 
		String[] headers = csv.next();	// headers
		Log.d(LOGTAG, "Fetched google spreadsheet with headers: "+Printer.str(headers));
		iso = new ISO3166();
		int bestYear = 2021; // HACK
		
		double totalCO2g = 0D;
		
		for (String[] row: csv) {
			// e.g. Afghanistan	AFG	2000	255.31914
			String country = row[0];			
			int year = Integer.parseInt(row[2]);
			if (year != bestYear) continue;
			String cc = iso.getCountryCode(country);
			if (cc==null) continue;
			// grams per kwh
			double co2g = Double.parseDouble(row[3]);
			totalCO2g += co2g;
			co2ForCountry.put(cc, co2g/1000); // change to kg/kwh
		}
		
		co2ForCountry.put(ZZ, totalCO2g/co2ForCountry.size()/1000);	// For "ZZ" use the average
	}
	
	private static final String ZZ = "ZZ";

	@Override
	public double getCO2PerKWh(String country, String regionIgnored, Time time) {
		String cc = iso.getCountryCode(country);
		if (cc==null || !co2ForCountry.containsKey(cc)) {
			Log.w(LOGTAG, "Unknown country: "+country);
			cc = ZZ;
		}
		double v = co2ForCountry.get(cc);
		return v;
	}

}

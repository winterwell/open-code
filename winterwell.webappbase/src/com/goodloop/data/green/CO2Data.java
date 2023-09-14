package com.goodloop.data.green;


import java.io.StringReader;

import com.goodloop.data.KAdFormat;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.io.CSVSpec;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.FakeBrowser;

/**
 * Core of our GL Auto carbon scoring
 *  
 * See: https://github.com/good-loop/impact/tree/master/carbon
 * 
 * @author geoff, dan
 * @testedby CO2DataTest
 */
public class CO2Data {
	private static final String LOGTAG = "CO2Data";
	
	/**
	 * TODO from data
	 * 
	 * Weighted average of the publisher data we hold
	 */
//	Do we introduce a country-neutral score for emissions for benchmarks??
//	Or do country by country?? (probably simpler)
	static final double WTAVG_DESKTOP;
	static final double WTAVG_MOBILE;

	private static IElectricityData electricityData = new AnnualElectricityData();
		
	@Deprecated
	static record DomainSurvey(Double desktopMBperAd, Double mobileMBperAd, Integer sspCount) {}

	
	private static int neverZero(int value) {
		if (value==0) return 1;
		else return value;
	}
	
	/**
	 * From the POC prep: https://docs.google.com/spreadsheets/d/18I2ndCW_uI2QhzOHuUFmSRqq20rQAxknptQDwVz0-WE/edit#gid=0 
	 */
	private static final double MB_VIDEO_CREATIVE = 3.77;
	static final double MB_DISPLAY_CREATIVE = 0.21;
	/**
	 * See https://github.com/good-loop/impact/blob/master/carbon/data-to-electricity.md
	 */
	private static final double KWH_PER_MB = 0.81 * 0.75 / 1024;
	
	/**
	 * @deprecated from data please!
	 * 
	 * from https://docs.google.com/spreadsheets/d/1iDAJf72tkRk7ZTBXKBAxrFW2IZRgdimka1gyA8NeM3I/edit#gid=367059901
	 */
	private static final int WTAVG_SSPS; // = 107;

	/**
	 * Score a SINGLE impression
	 * 
	 * @param mediaType "video" "display"
	 * @param country
	 * @param region
	 * @param ts
	 * @param publisherMB
	 * @param covered
	 * @param survey
	 * @param creativeMB <=0 to use a default
	 * @return
	 */
	public static CO2 getCo2(KAdFormat mediaType, String country, String region, Time time, 
			double publisherMB, boolean covered, Number sspCount, double creativeMB) 
	{
		if (creativeMB<=0) {
			// TODO don't use magic numbers for these
			creativeMB = KAdFormat.video == mediaType? MB_VIDEO_CREATIVE : MB_DISPLAY_CREATIVE;
		}
		if (publisherMB < 0 || creativeMB < 0 || (sspCount!=null && sspCount.doubleValue() < 0)) {
			throw new IllegalArgumentException("negative inputs?!");
		}
		// done as kgCO2e not MB as this is global 
		double supplyPathCO2 = getSupplyPathCO2(sspCount);

		double co2PerMB = getCO2PerMB(country, region, time);

		return new CO2(publisherMB*co2PerMB, creativeMB*co2PerMB, supplyPathCO2, covered);
	}

	
	static double getSupplyPathCO2(Number _sspCount) {
		// TODO country? but adtech is global
		double sspCount = _sspCount==null? WTAVG_SSPS : _sspCount.doubleValue();
		// fitted line, grams co2 
		double supplyPathCO2 = -0.0334 + 0.00359*sspCount;
		// min/max caps
		double co2 = Math.max(supplyPathCO2, 0.1);
		co2 = Math.min(co2, 1.5);
		return co2 / 1000;
	}


	/**
	 * The CO2 per MB of data to use for a given time and country
	 * 
	 * @param country
	 * @param ts
	 * @return kg/kwh
	 */
	private static double getCO2PerMB(String country, String region, Time time) {
		double co2PerKwh =  electricityData.getCO2PerKWh(country, region, time);
		double co2PerMb = co2PerKwh * KWH_PER_MB;

		return co2PerMb;
	}
	
	
	/**
	 * Data class representing a CO2 result. Use kgCO2 as the unit!
	 */
	public static record CO2(double publisher, double creative, double supplyPath, boolean covered) {

		public double getTotal() {
			return publisher+creative+supplyPath;
		}
	}
	
	/** CO2 (using some generic settings -- it is fine that these are arbitrary, as long as we keep them the same for consistency) */
	public static CO2 getCo2Generic(double meanMbperad, double meanMbperadbml, Number sspCount) {
		double mbperadAvg = (meanMbperad+meanMbperadbml)/2;
		return getCo2(KAdFormat.display, "GB", null, new Time(2023,6,21), mbperadAvg, 
				true, sspCount, MB_DISPLAY_CREATIVE);
	}


	
}

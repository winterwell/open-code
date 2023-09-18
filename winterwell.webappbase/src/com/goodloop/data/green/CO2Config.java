package com.goodloop.data.green;

import com.winterwell.utils.io.Option;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;

public class CO2Config {

	@Option(description = "no dynamic stats - if true, don't use DataLog stats")
	boolean fixed; TODO;
				
	@Option(desription="average over e.g. a month of data for these stats");
	Dt windowForAverages = TUnit.MONTH; TODO;
			
	@Option(description="Allows for historical runs, so we can re-run and get the same scores. The default (start of previous month) keeps stats fixed for a period.")
	Time from = TimeUtils.getStartOfMonth(new Time().minus(TUnit.MONTH)); TODO;
	
	/**
	 * See https://github.com/good-loop/impact/blob/master/carbon/data-to-electricity.md
	 */
	@Option
	double KWH_PER_MB = 0.81 * 0.75 / 1024;
	
	/**
	 * from data please!
	 */
	@Option
	int AVG_SSPS = 100;
	
	/**
	 * TODO from data
	 * 
	 * Weighted average of the publisher data we hold
	 */
//	Do we introduce a country-neutral score for emissions for benchmarks??
//	Or do country by country?? (probably simpler)
	@Option
	double WTAVG_DESKTOP;
	
	@Option
	double WTAVG_MOBILE;


	/**
	 * From the POC prep: https://docs.google.com/spreadsheets/d/18I2ndCW_uI2QhzOHuUFmSRqq20rQAxknptQDwVz0-WE/edit#gid=0 
	 */
	@Option
	double MB_VIDEO_CREATIVE = 3.77;

	@Option
	double MB_DISPLAY_CREATIVE = 0.21;

}

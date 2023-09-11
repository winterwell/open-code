package com.goodloop.data.green;

import com.winterwell.utils.time.Time;

public interface IElectricityData {

	/**
	 * 
	 * @param country
	 * @param region
	 * @param time
	 * @return kg/kwh
	 */
	double getCO2PerKWh(String country, String region, Time time);

	
}

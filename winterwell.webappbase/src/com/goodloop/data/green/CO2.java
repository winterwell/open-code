package com.goodloop.data.green;

/**
 * Data class representing a CO2 result. Use kgCO2 as the unit!
 */
public record CO2(double publisher, double creative, double supplyPath, boolean covered) {

	public double getTotal() {
		return publisher+creative+supplyPath;
	}
}
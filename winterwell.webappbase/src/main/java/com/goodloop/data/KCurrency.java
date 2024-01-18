package com.goodloop.data;

import java.util.Currency;

/**
 * // ISO 4217 £
 * https://www.iso.org/iso-4217-currency-codes.html
 * 
 * 
 * ?? Refactor to replace with {@link Currency}??
 * 
 * @author daniel
 *
 */
public enum KCurrency {
	AUD("A$", "Australian Dollar"),
	GBP("£", "Sterling"),
	CAD("C$", "Canadian Dollar"),
	CHF("CHF", "Swiss Franc"),
	CNY("￥", "Renminbi"), // Chinese, also (sort of) called the Yuan
	EUR("€", "Euro"), 
	MXN("MX$"), 
	NZD("NZ$", "New Zealand Dollar"),
	JPY("￥", "Yen"),
	SGD("S$"),
	TRY("₺", "Lira"), // Turkey
	USD("$", "Dollar"),
	ZAR("R"),
	/**
	 * HACK: allow Money objects to also represent %s and other multipliers. 
	 * @deprecated Not actually, but use with caution 
	 */
	MULTIPLY("x");
	
	public final String symbol;
	
	public final String name;
	
	KCurrency(String symbol) {
		this(symbol, null);
	}
	KCurrency(String symbol, String name) {
		this.symbol = symbol;
		this.name = name;
	}
	
}

package com.goodloop.data;

import com.winterwell.data.AThing;
import com.winterwell.data.PersonLite;

/**
 * see https://schema.org/Quotation
 * @author daniel
 *
 */
public class Quotation extends AThing {

	PersonLite author;
	
	String text;
	/**
	 * url to video??
	 */
	String video;
	
}

package com.winterwell.web.fields;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import com.winterwell.utils.StrUtils;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeParser;
import com.winterwell.utils.time.TimeUtils;

import jakarta.mail.internet.MailDateFormat;

/**
 * Enter dates (and times). TODO a (popup) javascript calendar widget. TODO
 * handle time zones configurably
 * 
 * Note: This uses a human-readable format which can lose upto a second of precision.
 * 
 * @see TimeField which is more flexible
 * TODO gut this, and use TimeField to do the heavy lifting.
 * 
 * @see DateFormatField which is more rigid / predictable
 * @author daniel
 * @testedby  DateFieldTest}
 */
public class DateField extends AField<Time> {	
	
	private static final long serialVersionUID = 1L;


	TimeParser tp = new TimeParser();
	

	public DateField(String name) {
		super(name, "text");
		// The html5 "date" type is not really supported yet.
		// What it does do on Firefox is block non-numerical text entry, which
		// we want to support
		cssClass = "DateField";
	}

	/**
	 * First tries the "canonical" "HH:mm dd/MM/yyyy", then the other formats,
	 * finally {@link TimeUtils#parseExperimental(String)}.
	 */
	@Override
	public Time fromString(String v) {
		return tp.parse(v);
	}

	@Override
	public String toString(Time time) {
		return toString2(time);
	}
	
	static String toString2(Time time) {
		// BC?
		if (time.isBefore(TimeUtils.AD)) {
			// TODO include BC for a pretty string
			return Long.toString(time.getTime());
		}
		// send a human readable ISO8601 string
		String s = time.toISOString();

		//		((SimpleDateFormat) formats[0].clone()).format(time
//				.getDate());		
		// NOTE: SimpleDateFormat.parse ___AND___ SimpleDateFormat.format are
		// not thread safe...
		// hence the .clone
		// (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4228335) -
		// @richardassar
		return s;
	}
	
	@Override
	public Class getValueClass() {
		return Time.class;
	}
}

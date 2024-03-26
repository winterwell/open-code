package com.winterwell.web.app;

import java.awt.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.winterwell.utils.log.ILogListener;
import com.winterwell.utils.log.Report;

/**
 * Bridge from winterwell Log to SLF4J.
 * Recommendation: Use SLF4J directly instead.
 */
public final class SLF4JLogListener implements ILogListener {

	Logger logger;
	
	public SLF4JLogListener() {
		String name = AMain.main==null? "logger" : AMain.main.getAppNameLocal();
		logger = LoggerFactory.getLogger(name);
	}
	
	@Override
	public void listen(Report report) {
		StringBuilder sb = new StringBuilder();
		sb.append('#'); sb.append(report.tag); sb.append('\t');
		sb.append(report.getMessage());
		if (report.ex!=null) {
			sb.append('\t'); sb.append(report.getDetails());
		}
		// TODO levels etc
		logger.info(sb.toString());
	}
	
}
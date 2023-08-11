package com.goodloop.data;

import java.util.Map;

import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.TUnit;

/**
 * Based on https://schema.org/VideoObject
 * 
 * @author daniel
 *
 */
public class VideoObject extends MediaObject // these do have IDs and urls. Do we ever store them as top-level DB objects??
{

	/**
	 * e.g. nopreload
	 */
	Map advanced;
	

	/**
	 * In the format e.g. "16:9" NB: not in schema.org
	 * 
	 * Warning: old data can be in double form (e.g. 16:9 = 1.77778)
	 */
	String aspect;


	private Dt duration;

	/**
	 * "video" or "vast-vpaid"
	 */
	String format;

	Boolean mobile;

	/**
	 * True if this video is a VAST tag for a Good-Loop-owned inventory item on an
	 * SSP, and should only be loaded from a page on a *.good-loop.com domain.
	 */
	Boolean nested;

	/**
	 * @deprecated lets use url + format
	 */
	String vastTag;

	@Deprecated // old
	private Integer videoSeconds;

	public Dt getDuration() {
		if (duration == null && videoSeconds != null && videoSeconds > 0)
			duration = new Dt(videoSeconds, TUnit.SECOND);
		return duration;
	}

	public String getFormat() {
		return format;
	}

	/**
	 * TODO set duration
	 */
	@Override
	public void init() {
		super.init();
		// duration??
		getDuration(); // fix old videoSeconds
		// file-size??
		if (contentSize == null) {
			// TODO try to measure the content size -- but don't block up the thread??
			// Can we get the uploader to provide this info??
		}
	}

	public void setAspect(String aspect) {
		this.aspect = aspect;
	}

	public void setDuration(Dt duration) {
		this.duration = duration;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public void setMobile(boolean mobile) {
		this.mobile = mobile;
	}

	public void setNested(boolean nested) {
		this.nested = nested;
	}


}

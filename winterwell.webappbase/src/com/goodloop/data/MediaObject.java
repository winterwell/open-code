package com.goodloop.data;

import java.util.List;

import com.winterwell.data.AThing;
import com.winterwell.utils.Utils;
import com.winterwell.utils.time.Time;

public class MediaObject extends AThing {

	String caption;
	List<String> thumbnailUrl;
	Time uploadDate;
	/**
	 * Actual bytes of the video file.
	 * Often blank or the same as {@link #getUrl()} -- but it can be that
	 * `url` is a page displaying the video (a la YouTube).
	 * @see #getUrl() 
	 */
	protected String contentUrl;
	Integer width;
	Integer height;
	/**
	 * in mb (or should we make this numerical??)
	 */
	String contentSize;

	public MediaObject() {
		super();
	}
	
	public void setWidth(Integer width) {
		this.width = width;
	}
	
	public void setHeight(Integer height) {
		this.height = height;
	}


	public String getContentUrl() {
		return Utils.or(contentUrl, getUrl());
	} 
}
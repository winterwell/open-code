package com.goodloop.data;

/**
 * Based on https://schema.org/ImageObject
 * @see VideoObject
 * @author daniel
 *
 */
public class ImageObject extends MediaObject {

	public ImageObject() {
		super();
	}
	public ImageObject(String url) {
		super();
		this.url = url;
	}

	
	
//	/**
//	 * ??is this used??
//	 * Can this image be used as a backdrop in page designs? Must be high-res and nice :))
//	 */
//	Boolean backdrop;


}

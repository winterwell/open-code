/**
 * 
 */
package com.winterwell.web;


/**
 * @author daniel
 *
 */
public class FileTooLargeException extends WebInputException {

	private static final long serialVersionUID = 1L;

	public FileTooLargeException(String msg) {
		super(msg);
	}

}

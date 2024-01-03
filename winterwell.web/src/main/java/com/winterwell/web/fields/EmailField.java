package com.winterwell.web.fields;

import com.winterwell.web.WebInputException;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * For emails (with some safety checking)
 * @author daniel
 */
public class EmailField extends AField<InternetAddress> {
	
	public EmailField(String name) {
		super(name, "email");
	}

	private static final long serialVersionUID = 1L;
	
	@Override
	public InternetAddress fromString(String v) throws Exception {
		try {
			InternetAddress ia = new InternetAddress(v.toLowerCase());
			// safety check, since new InternetAddress("bob") will succeed
			if (ia.getAddress().indexOf('@')==-1) {
				throw new WebInputException("Not a valid email: "+v);	
			}
			return ia;
		} catch (AddressException e) {
			throw new WebInputException(v+" is not a valid email address");
		}
	}

	@Override
	public String toString(InternetAddress value) {
		return value.toString();
	}

	@Override
	public Class<InternetAddress> getValueClass() {
		return InternetAddress.class;
	}
}

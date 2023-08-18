package com.winterwell.web;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.regex.Matcher;

import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.data.XId;

public class WebEx extends RuntimeException {
	
	/**
	 * @return Can be empty, never null.
	 */
	@Override
	public String getMessage() {	
		String m = super.getMessage();		
		return m == null? "" : m;
	}
	
	/**
	 * Loop detected
	 */
	public static class E508Loop extends WebEx.E50X {
		private static final long serialVersionUID = 1L;
		public E508Loop(String msg, Throwable e) {
			super(508, null, msg+" "+Printer.toString(e, true));
		}
	}

	/**
	 * The request is well-formed and the resource exists (so not a 404), 
	 * but there is a semantic problem with it, e.g. a missing parameter.
	 */
	public static class E422Unprocessable extends E40X {
		public E422Unprocessable(String msg) {
			super(422, null, msg);
		}
		private static final long serialVersionUID = 1L;
	}

	/**
	 * e.g. a version conflict in elastic-search
	 * @author daniel
	 *
	 */
	public static class E409Conflict extends E40X {
		public E409Conflict(String msg) {
			this(null, msg);
		}
		public E409Conflict(String url, String msg) {
			super(409, url, msg);
		}
		private static final long serialVersionUID = 1L;
	}


	private static final long serialVersionUID = 1L;
	/** HTTP code, e.g. 404 for file not found */
	public final int code;

	public WebEx(int code, String msg) {
		this(code, msg, null);
	}
	
	public WebEx(int code, String msg, Throwable e) {
		super(code+": "+sanitise(msg), e);
		this.code = code;
	}

	/**
	 * There's a clever hack where you can craft a url,
	 * where our error message shows the attackers content.
	 * So let's guard against bad content.
	 */
	private static String sanitise(String msg) {
		if (msg==null) return null;
		String m2 = WebUtils2.stripTags(msg);
		// href?
		Matcher m = WebUtils2.URL_REGEX.matcher(m2);
		String m3 = m.replaceAll("(untrusted) $0");
		return m3;
	}

	/**
	 * It's YOUR fault dear visitor
	 */
	public static class E40X extends WebEx {
		private static final long serialVersionUID = 1L;

		public E40X(int code, String url) {
			super(code, url);
		}

		public E40X(int code, String url, String msg) {
			super(code, StrUtils.joinWithSkip(" ", msg, url));
		}
		
		public E40X(int code, String msg, Throwable e) {
			super(code, msg, e);
		}		
	}

	/**
	 * Request Header Fields Too Large -- WHat is this caused by??
	 */
	public static class E431 extends E40X {
		private static final long serialVersionUID = 1L;

		public E431(String url) {
			super(431, url);
		}

		public E431(String url, String msg) {
			super(431, StrUtils.joinWithSkip(" ", msg, url));
		}
		
		public E431(String msg, Throwable e) {
			super(431, msg, e);
		}		
	}
	
	 

	/**
	 * Wrap an exception to indicate the causing input web parameter
	 */
	public static class BadParameterException extends E40X {		
		public BadParameterException(String parameter, Object value) {
			this(parameter, value, null);
		}
		public BadParameterException(String parameter, Object value, Throwable ex) {
			super(400, "Bad parameter: "+parameter+(value==null? "" : "="+value), ex);
		}		
		private static final long serialVersionUID = 1L;		
	}
	
	/**
	 * E429
	 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/429
	 * @author daniel
	 *
	 */
	public static class RateLimitException extends E40X {
		private static final long serialVersionUID = 1L;
		public RateLimitException(String msg) {
			super(429, msg);			
		}
		
		public RateLimitException(XId user, String msg) {
			this(StrUtils.joinWithSkip(" ", user,  msg));
		}
		/**
		 * (if known) when to retry
		 */
		public Time retry;
		
		public RateLimitException setRetryAfter(String ra) {
			if (ra==null) return this;
			try {
				if (StrUtils.isInteger(ra)) {
					retry = new Time().plus(Integer.valueOf(ra), TUnit.SECOND);
				} else {
					retry = new Time(ra);
				}
			} catch(Throwable ex) {
				Log.e("(swallow) Retry-After "+ex);
			}
			return this;
		}
	}
	
	
	/**
	 * Forbidden
	 */
	public static class E403 extends E40X {
		/**
		 * Forbidden
		 */
		public E403(String url) {
			super(403, url);
		}
		/**
		 * Forbidden
		 */
		public E403(String url, String msg) {
			super(403, url, msg);
		}
		private static final long serialVersionUID = 1L;
	}

	
	/**
	 * 401: Unauthorised. The request requires user authentication, and none or invalid was given. 
	 */
	public static class E401 extends E40X {
		public E401(String url) {
			super(401, url);
		}
		public E401(String url, String msg) {
			super(401, url, msg);
		}
		public E401() {
			this(null, "Unauthorised - please login");
		}
		private static final long serialVersionUID = 1L;
	}
	
	public static class E404 extends E40X {
		public E404(String url) {
			super(404, url);
		}
		public E404(String url, String msg) {
			super(404, url, msg);
		}
		private static final long serialVersionUID = 1L;
	}
	
	/**
	 * The server cannot or will not process the request due to something that is perceived to be a client error 
	 * (e.g., malformed request syntax, invalid request message framing, or deceptive request routing).
	 */
	public static class E400 extends E40X {
		public E400(String msg) {
			super(400, null, msg);
		}
		public E400(String url, String msg) {
			super(400, url, msg);
		}		
		private static final long serialVersionUID = 1L;
	}

	/**
	 * Treat the (rare) 410 as a subclass of the common 404
	 * @author daniel
	 *
	 */
	public static class E410 extends E404 {
		public E410(String url) {
			super(url);
		}
		public E410(String url, String msg) {
			super(url, msg);
		}
		private static final long serialVersionUID = 1L;
	}
	
	/**
	 * Server error
	 */
	public static class E50X extends WebEx {
		/**
		 * Wrap another exception to mark it as a 50X in disguise.
		 * @param ex
		 */
		public E50X(Throwable ex) {
			super(500, ex.getMessage(), ex);
		}
		public E50X(int code, String url, String msg) {
			super(code, StrUtils.joinWithSkip(" ", msg, url));
		}
		private static final long serialVersionUID = 1L;
		
		
//		// Exception wrapping code -- should we move this into WebEx??
//		private Throwable error() {
//			return Utils.or(getCause(), this);
//		}
		/**
		 * The original Throwable
		 */
		@Override
		public final Throwable getCause() {
			Throwable ex = super.getCause();
			return ex;
		}

		@Override
		public final StackTraceElement[] getStackTrace() {
			return getCause()==null? super.getStackTrace() : getCause().getStackTrace();
		}

		@Override
		public void printStackTrace(PrintStream s) {
			if (getCause()==null) {
				super.printStackTrace(s);
			} else {
				getCause().printStackTrace(s);
			}
		}

		@Override
		public void printStackTrace(PrintWriter s) {
			if (getCause()==null) {
				super.printStackTrace(s); // otherwise you get a stackoverflow
			} else {
				getCause().printStackTrace(s);
			}
		}

		/**
		 * Hide the WrappedException bit - show the underlying exception
		 */
		@Override
		public String toString() {
			Throwable e = getCause();
			return e == null ? super.toString() : getClass().getSimpleName()+" wraps "
					+ getCause().toString();
		}

	}


	/**
	 * 523 Origin Is Unreachable
    Could not reach the origin server; for example, if the DNS records for the origin server are incorrect or missing.
	 */
	public static final class E523 extends E50X {

		public E523(String domain, String msg) {
			super(523, domain, msg);
		}

		private static final long serialVersionUID = 1L;
		
	}

	public static class Redirect extends WebEx {
		private static final long serialVersionUID = 1L;
		public final String to;
	
		public Redirect(int code, String from, String to) {
			super(code, from+" -> "+to);
			this.to = to==null? null : to.trim();
		}
	}


	/**
	 * What error to throw?
	 * Note: FakeBrowser has separate and better error handling (which draws on more info).
	 * @param code
	 * @param url
	 * @return
	 */
	public static WebEx fromErrorCode(int code, String url, String msg) {
		// Not an error?!
		if (code<300) return null;
		if (code>=300 && code <400) {
			String to = null;
			if (msg!=null && WebUtils2.URL_REGEX.matcher(msg).matches()) {
				to = msg;
			}
			return new WebEx.Redirect(code, url, to);
		}
		switch(code) {
		case 401: return new WebEx.E401(url, msg);
		case 403: return new WebEx.E403(url, msg);
		case 404: return new WebEx.E404(url, msg);
		case 410: return new WebEx.E410(url, msg);
		}
		if (code>=400 && code <500) return new WebEx.E40X(code, url, msg);
		if (code>=500) return new WebEx.E50X(code, url, msg);
		return new WebEx(code, msg);
	}

}

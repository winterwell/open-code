package com.winterwell.web.app;

import java.io.IOException;
import java.util.function.Supplier;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.io.EofException;

import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.WrappedException;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.WebEx;

/**
 * Turn IServlet classes (which get created fresh for each request) into HttpServlet objects (which stick around).
 * Adds in resource clean-up and error handling.
 * @author daniel
 *
 */
public class HttpServletWrapper extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static boolean debugAll;
	
	Supplier<IServlet> factory;

	private boolean debug;

	private Class<? extends IServlet> servletClass;
	
	public HttpServletWrapper setDebug(boolean debug) {
		this.debug = debug;
		return this;
	}
	
	public HttpServletWrapper(Supplier<IServlet> factory) {
		this.factory = factory;
	}

	/**
	 * Convenient way to make a wrapper
	 * @param servlet
	 */
	public HttpServletWrapper(Class<? extends IServlet> servlet) {
		this(() -> {
			try {
				return servlet.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw Utils.runtime(e);
			}
		});
		servletClass = servlet;
	}

	@Override
	public String toString() {
		if (servletClass!=null) return "HttpServletWrapper[" + servletClass+ "]";
		return "HttpServletWrapper[factory=" + factory + "]";
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		WebRequest state = null;
		String sname = null;
		try {
			state = new WebRequest(req, resp);			
			IServlet servlet = getServlet(state);
			// log everything?
			sname = servlet.getClass().getSimpleName();
			if (debug || debugAll) {
				Log.d(sname, state);
			}
			Thread.currentThread().setName(sname);
			servlet.process(state);
		} catch(EofException eof) {
			Log.i(sname, eof+" for "+state); // handle EOF quietly
		} catch (Throwable ex) {
			doCatch(ex, resp, state);
		} finally {
			WebRequest.close(req, resp);
		}
	}

	protected IServlet getServlet(WebRequest state) {
		 return factory.get();
	}

	public static void doCatch(Throwable ex, HttpServletResponse resp, WebRequest state) {		
		// include state info in the string
		if (state!=null) {
			String s = 
					StrUtils.join(state.getMessages(), ", ")
					+ state.toString();
			ex = new WrappedException(s, ex);
		}
		// send a stacktrace? Yes, except for e.g. "not logged in"
		boolean incStacktrace = true;
		if (ex instanceof WebEx && ((WebEx) ex).code < 500) {
			incStacktrace = state.debug; // bad request - no stacktrace unless requested with debug=true
		}
		String exs = Printer.toString(ex, incStacktrace);
		
		WebEx wex = WebUtils2.runtime(ex);
		if (wex.code >= 500) {
			Log.e("error."+wex.getClass().getSimpleName(), exs);
		} else {
			Log.w(wex.getClass().getSimpleName(), exs);
		}

		// are we in an nginx loop? Where nginx keeps getting the same error, and trying different servers
		if (state != null) {
			String ip = state.getRemoteAddr();
			String ip2 = state.getRequest().getRemoteAddr();
			String[] ips = (ip+","+ip2).split(",\\s*");
			if (ips.length > 30) {
				wex = new WebEx.E508Loop(state.toString(), ex);
			}
		}
		
		WebUtils2.sendError(wex.code, wex.getMessage()+" \n\n<details>"+exs+"</details>", resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}
	
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	public static void setDebugAll(boolean b) {
		debugAll = b;
	}
	
//	@Override
//	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//		// TODO Auto-generated method stub
//		super.doHead(req, resp);
//	}
}

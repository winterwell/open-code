package com.winterwell.web.app;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.winterwell.utils.Utils;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.WebEx;

/**
* Example use in an AMain subclass:
* <code><pre>
* 
* 	protected void addJettyServlets(JettyLauncher jl) {
		super.addJettyServlets(jl);
		MasterServlet ms = jl.addMasterServlet();	
		ms.addServlet("/foo", FooServlet.class);
	}
	
* </pre></code>
*/
public class MasterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private boolean debug = true;

	private Map<String,Class> classForPrefix = new HashMap();
	private Map<String,IServlet> singletonForPrefix = new HashMap();

	private FileServlet fileServlet;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}
	
	public MasterServlet() {
	}
	
	/**
	 * Switch CORS on
	 */
	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		// https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Allow-Origin
		// "*" will fail if credentials are supplied!
		String auth = req.getHeader("Authorization");
		WebRequest state = new WebRequest(req, response);
		String ref = state.getReferer();
//		System.out.println(state);
//		System.out.println(state.getRequestHeaderMap());
		String origin = state.getOrigin();
		if (origin==null) origin = "*";
		response.setHeader("Access-Control-Allow-Origin", origin);
		if ( ! "*".equals(origin)) response.setHeader("Vary","Origin");
		// https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Allow		
		response.setHeader("Allow", "GET, POST, PUT, DELETE, OPTIONS");		
		response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
		String reqHeaders = state.getRequestHeader("Access-Control-Request-Headers");
		String allowHeaders = reqHeaders==null? "Content-Type, Accept, Origin, Authorization" : reqHeaders;
		response.setHeader("Access-Control-Allow-Headers", allowHeaders);
	    response.setStatus(HttpServletResponse.SC_OK);
	}

	@Override
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Hm... ALlow the IServlet to handle this??
		super.doHead(req, resp);
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
	}

	private IServlet getMakeServlet(String servletName) throws Exception {
		Class klass = classForPrefix.get(servletName);
		if (klass!=null) {
			IServlet s = (IServlet) klass.newInstance();
			return s;
		}
		IServlet s = singletonForPrefix.get(servletName);
		if (s != null) {
			return s;
		}
		if (fileServlet!=null) {
			return fileServlet;
		}
		throw new WebEx.E404(null, "No such servlet: "+servletName);
	}

	protected String servletNameFromPath(String path) {
		String[] pathBits = path.split("/");
		if (pathBits.length==0) {
			if (fileServlet!=null) return "FileServlet";
			throw new WebEx.E400("No servlet?! This can mean a mis-configured server not serving index.html");
		}
		// NB: paths always start with a / so pathBits[0]=""
		return FileUtils.getBasename(pathBits[1]);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		WebRequest state = null;
		String servletName = "servlet";
		try {
			state = new WebRequest(req, resp);
			// everyone wants CORS
			WebUtils2.CORS(state, false);
			// servlet
			String path = state.getRequestPath();		
			if (path==null) {
				throw new WebEx.E403(null, "Specify a servlet path");
			}
			servletName = servletNameFromPath(path);		
			Thread.currentThread().setName("servlet: "+servletName);
			// make a servlet
			IServlet s = getMakeServlet(servletName);					
			if (debug) {
				Log.d(servletName, state);
			}
			// do stuff
			s.process(state);
		} catch(Throwable ex) {
			Log.w(servletName, ex);
			HttpServletWrapper.doCatch(ex, resp, state);
		} finally {
			Thread ct = Thread.currentThread();
			ct.setName("...done: "+ct.getName());
			WebRequest.close(req, resp);
		}
	}

	public void setDebug(boolean b) {
		this.debug = b;
	}

	/**
	 * 
	 * @param path e.g. "foo" or "/foo" or "/foo/*" 
	 * 	Leading / and trailing /* are handled as equivalent
	 * @param klass
	 */
	public void addServlet(String path, Class<? extends IServlet> klass) {
		Utils.check4null(path, klass);
		// / * is an annoyingly fiddly part of the standard J2EE -- lets make it irrelevant
		if (path.endsWith("*")) {
			path = path.substring(0, path.length()-1);
		} 
		if (path.endsWith("/")) {
			path = path.substring(0, path.length()-1);
		}
		// chop leading /
		if (path.startsWith("/")) {
			path = path.substring(1, path.length());
		}
		assert ! path.contains("/") : path;
		
		classForPrefix.put(path, klass);
	}
	
	/**
	 * 
	 * @param path e.g. "foo" or "/foo" or "/foo/*" 
	 * 	Leading / and trailing /* are handled as equivalent
	 * @param singletonServlet Used as a "static" object
	 */
	public void addServlet(String path, IServlet singletonServlet) {
		Utils.check4null(path, singletonServlet);
		// a safety check
		Class<?> superClass = singletonServlet.getClass().getSuperclass();
		assert superClass==null || ! superClass.getSimpleName().equals("CrudServlet") : "use the fresh-servlets addServlet(String, Class) method";		
		// / * is an annoyingly fiddly part of the standard J2EE -- lets make it irrelevant
		if (path.endsWith("*")) {
			path = path.substring(0, path.length()-1);
		} 
		if (path.endsWith("/")) {
			path = path.substring(0, path.length()-1);
		}
		// chop leading /
		if (path.startsWith("/")) {
			path = path.substring(1, path.length());
		}
		assert ! path.contains("/") : path;
		
		singletonForPrefix.put(path, singletonServlet);
	}

	@Override
	public String toString() {
		return "MasterServlet"+classForPrefix;
	}

	public void setFileServlet(FileServlet fileServlet) {
		this.fileServlet = fileServlet;
	}
}

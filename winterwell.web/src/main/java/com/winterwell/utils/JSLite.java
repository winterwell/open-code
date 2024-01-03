package com.winterwell.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSLite {

	public JSLite(SimpleTemplateVars stv) {
		this.stv = stv;
	}
	
	SimpleTemplateVars stv;	
	static Pattern pdb = Pattern.compile("\\$\\{([^\\}]*)\\}");
	static Pattern pfn = Pattern.compile("^([a-zA-Z0-9_]+)\\((.*?)\\)$");
	private Map<String, IFn> fns;
	
	public String process(String txt) {
		String txtOut = StrUtils.replace(txt, pdb, (StringBuilder sb, Matcher m) -> {
			String innards = m.group(1);
			// e.g. support {mytest($myvar)? "a" : "b"}
			String processedInnards = stv.process2_vars(innards);
			
			Matcher pfnm = pfn.matcher(processedInnards);
			if (pfnm.matches()) {
				String fname = pfnm.group(1);
				String farg = pfnm.group(2);
				IFn fn = fns.get(fname);
				if (fn!=null) {
					processedInnards = StrUtils.str(fn.apply(farg));
				}
			}
			if (processedInnards != null) sb.append(processedInnards);
		});
		return txtOut;
	}
	
	public JSLite setFns(Map<String, IFn> fns) {
		this.fns= fns;
		return this;
	}

}

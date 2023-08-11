/**
 * 
 */
package com.winterwell.datalog;

import java.util.List;

import com.winterwell.utils.Dep;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.AjaxMsg;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.data.XId;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * @author daniel
 *
 */
public class DataLogSecurity {

	public static void check(WebRequest state, Dataspace dataspace, List<String> breakdown) {		
		XId user = state.getUserId();
		if (user==null) {
			YouAgainClient yac = Dep.get(YouAgainClient.class);
			List<AuthToken> u = yac.getAuthTokens(state);
			if (u==null) {
				state.addMessage(new AjaxMsg(new SecurityException("DataLogSecurity: not logged in")));
			}
		}
		// block dangerous breakdowns
		for (String b : breakdown) {
			for(String pii : DataLogEvent.COMMON_PROPS_PII) {
				// not this pii?
				if ( ! b.contains(pii)) continue;
				// HACK: allow e.g. blah{user:cardinality}
				Breakdown bd = Breakdown.fromString(b);
				if (bd.op == KBreakdownOp.cardinality || bd.op == KBreakdownOp.histogram) {
					// make sure blah is OK before we continue
					if ( ! bd.getBy().contains(pii)) {
						continue;
					}
				}
				throw new WebEx.E403("Cannot breakdown by "+breakdown);
			}			
		}
		
		// TODO check shares with YouAgain -- all the authd XIds		
		
		// assume all OK!
	}

}

package com.winterwell.youagain.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.Warning;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.ajax.JThing;
import com.winterwell.web.data.XId;
/**
 * 
 * @testedby ShareClientTest
 * @author daniel
 *
 */
public final class ShareClient {

	public static final String ACTION_SHARE = "share";
	public static final String ACTION_DELETE_SHARE = "delete-share";
	public static final String ACTION_CLAIM = "claim";
	private static final String LOGTAG = "ShareClient";

	ShareClient(YouAgainClient youAgainClient) {
		this.yac = youAgainClient;
	}

	YouAgainClient yac;

	/**
	 * Convenience for {@link #getSharedWith(List, String)} that strips the prefix off.
	 * TODO should this cache the results??
	 * @param app HACK override the config setting! Done as a safe hack to get the GAT demo going
	 * @param tokens
	 * @param type
	 * @return
	 */
	public List<String> getSharedWithItemIds(String dataspace, String app, List<AuthToken> tokens, String type) {
		List<String> sharedWith = null;
		String prefix = type+":";
		try {
			assert ! type.isEmpty() && ! type.contains(":") : type;			
			sharedWith = getSharedWith(tokens, prefix, dataspace, app);
			int n = prefix.length();
			List<String> sharedCampaigns = Containers.apply(sharedWith, sw -> sw.substring(n));
			return sharedCampaigns;
		} catch(Throwable ex) { // FIXME bug July 2022 - delete when fixed
			Log.e(prefix+" -> "+sharedWith+" "+Printer.toString(ex, true));
			return new ArrayList();
		}
	}
	
	/**
	 * 
	 * @param authToken TODO manage this better
	 * @param prefix Optional
	 * @return
	 */
	public List<String> getSharedWith(AuthToken at, String prefix) {
		return getSharedWith(Collections.singletonList(at), prefix);
	}
	
	public List<String> getSharedWith(List<AuthToken> auths, String prefix) {
		return getSharedWith(auths, prefix, "anon-client-app", yac.iss);
	}
	
	/**
	 * 
	 * @param auths
	 * @param prefix
	 * @param app HACK allow an override. Can we clean up the use of GL app names and drop this??
	 * @return
	 */
	List<String> getSharedWith(List<AuthToken> auths, String prefix, String dataspace, String app) 
	{
		try {
			FakeBrowser fb = new FakeBrowser();
			List<String> jwts = Containers.apply(auths, AuthToken::getToken);
			fb.setAuthenticationByJWTs(jwts);
			fb.setDebug(true);
			String response = fb.getPage(yac.yac.endpoint, new ArrayMap(
					"app", app,
					"d", dataspace,
					"action", "shared-with",
					"prefix", prefix));
			
			Map jobj = WebUtils2.parseJSON(response);
			List<Map> shares = SimpleJson.getList(jobj, "cargo");			
			List<String> items = Containers.apply(shares, share -> SimpleJson.get(share, "item"));
			// apply prefix (bug seen 1st July 2022)
			List<String> items2 = Containers.filter(items, item -> item.startsWith(prefix));
			if (items2.size() != items.size()) {
				Log.w(LOGTAG, "Bad prefix handling: "+prefix+" "+app+" "+items);
			}
			return items2;			
		} catch (WebEx.E401 e401) {
			Log.i("ShareClient.getSharedWith", new Warning(e401.toString()));	
		}
		return Collections.emptyList();
	}
	
	/** List the users a particular entity is shared to 
	 * @param auths */
	public List<ShareToken> getShareList(CharSequence share, List<AuthToken> auths) {
		// share-list needs a login, and temp ids won't work 
		auths = Containers.filter(auths, a -> ! a.isTemp());
		if (auths.isEmpty()) {
			Log.d(LOGTAG, "getShareList() aborted - No (non-temp) auths. "+share);
			return Collections.EMPTY_LIST;
		}
		FakeBrowser fb = yac.fb(auths);
		String response = fb.getPage(yac.yac.endpoint, new ArrayMap(
			"d", yac.iss,
			"action", "share-list",
			"entity", share.toString()
			));

		JSend.parse(response).getData();
		Map jobj = WebUtils2.parseJSON(response);
		Object shares = SimpleJson.get(jobj, "cargo");
		if (shares==null) return null;
		List<Map> lshares = Containers.asList(shares);
		List<ShareToken> sts = Containers.apply(lshares, sm -> new ShareToken((Map)sm));
		return sts;
	}
	
	/**
	 * 
	 * @param authToken Who authorises this share?
	 * @param item ID of the thing being shared.
	 * @param targetUser Who is it shared with?
	 */
	public ShareToken share(AuthToken authToken, String item, XId targetUser) {
		FakeBrowser fb = new FakeBrowser()
				.setDebug(true);
		fb.setAuthenticationByJWT(authToken.getToken());
		Map<String, String> shareAction = new ArrayMap(
			"action", ACTION_SHARE,
			"d", yac.iss,
			"shareWith", targetUser,
			"entity", item
		);
		// call the server
		String response = fb.getPage(yac.yac.endpoint, shareAction);
		
		JSend jsend = JSend.parse(response);		
		JThing d = jsend.getData();
		d.setType(ShareToken.class);
		Object st = d.java();
		return (ShareToken) st;
	}
	
	public boolean delete(AuthToken authToken, String item, XId targetUser) {
		FakeBrowser fb = new FakeBrowser()
				.setDebug(true);
		fb.setAuthenticationByJWT(authToken.getToken());
		Map<String, String> shareAction = new ArrayMap(
			"action", ACTION_DELETE_SHARE,
			"d", yac.iss,
			"shareWith", targetUser,
			"entity", item
		);
		// call the server
		fb.getPage(yac.yac.endpoint, shareAction);
		
		// No exception? It's done.
		if (fb.getStatus() >= 200 && fb.getStatus() < 400) return true;
		return false;
	}

	public boolean canWrite(AuthToken authToken, String item, List<ShareToken> shares) {
		Utils.check4null(authToken, item, shares);
		for (ShareToken shareToken : shares) {
			if ( ! item.equals(shareToken.getItem())) continue;
			if ( ! shareToken.write) continue;
			if (shareToken.getTo().contains(authToken.getXId())) {
				return true;
			}
		}
		return false;
	}

	public List<ShareToken> getShareStatus(ShareTarget share, List<AuthToken> auths) {
		if (auths.isEmpty()) {
			return Collections.EMPTY_LIST;
		}
		List<ShareToken> list = getShareList(share, auths);
		List<XId> myXIds = Containers.apply(auths, AuthToken::getXId);
		List<ShareToken> myShares = Containers.filter(list, st -> ! Collections.disjoint(st.getTo(), myXIds));
		return myShares;
	}
	
}

package com.winterwell.nlp.languages;

import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Cache;
//import com.winterwell.json.JSONObject;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;

/**
 * Out of date??
 * 
 * @testedby GoogleTranslateTest
 * @author daniel
 *
 */
public class GoogleTranslate {

	Cache<String, String> cache = new Cache(100).setStats("GoogleTranslate");
	private String src;

	private String target;

	/**
	 * Translate between languages using Google
	 * 
	 * @param src
	 *            e.g. "ar"
	 * @param target
	 *            e.g. "en"
	 */
	public GoogleTranslate(String src, String target) {
		this.src = src;
		this.target = target;
	}

	public String translate(String text) {
		// cut down on api usage
		String trans = cache.get(text);
		if (trans != null)
			return trans;
		FakeBrowser fb = new FakeBrowser();
		LoginDetails ld = Logins.get("google_com_language_translate");
		String json = fb.getPage(
				"https://www.googleapis.com/language/translate/v2",
				new ArrayMap("key", ld.apiKey,
						"source", src, "target", target, "q", text));
		try {
			Object jo = new SimpleJson().fromJson(json);
			trans = SimpleJson.get(jo, "data", "translations", 0, "translatedText");
//			JSONObject jo = new JSONObject(json);
//			trans = jo.getJSONObject("data").getJSONArray("translations")
//					.getJSONObject(0).getString("translatedText");
			cache.put(text, trans);
			assert trans != null : text;
			return trans;
		} catch (Exception e) {
			throw Utils.runtime(e);
		}
	}
}

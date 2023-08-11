package com.winterwell.datalog.server;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.winterwell.bob.tasks.WinterwellProjectFinder;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.log.Log;

/**
 * See also MeasureAdUtils
 * @author daniel
 *
 */
public class AdTechUtils {

	private static final String LOGTAG = "AdTechUtils";

	private static Set<String> adtechSites;
	
	public static boolean isTechSite(String domain) {
		if (adtechSites == null) {
			HashSet<String> _adtechSites = new HashSet();
			try {
				File f = new File("data/adtech-sites.csv");
				if ( ! f.exists()) {
					File dir = new WinterwellProjectFinder().apply("winterwell.datalog");
					f = new File(dir, "data/adtech-sites.csv");
					Log.i(LOGTAG, "using adtech sites csv: "+f);
				}
				CSVReader r = new CSVReader(f);
				for (String[] row : r) {
					if (row.length == 0) continue;
					String s = row[0];
					if (s==null) continue; //paranoia
					s = s.trim();
					if (s.isEmpty()) continue;
					_adtechSites.add(s);					
				}
			} catch(Throwable ex) {
				Log.e(LOGTAG, "isTechSite (fallback to no) "+ex);
			}
			adtechSites = _adtechSites; // NB: thread safety paranoia
		}
		return adtechSites.contains(domain);
	}

}

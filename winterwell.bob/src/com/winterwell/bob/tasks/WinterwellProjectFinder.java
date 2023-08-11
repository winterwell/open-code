package com.winterwell.bob.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.winterwell.utils.IFn;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.data.XId;

/**
 * TODO find the dir for a WW Eclipse or node project.
 * This is a place for WW specific hacks
 * TODO use this in e.g. EclipseClassPath
 * 
 * @testedby {@link WinterwellProjectFinderTest}
 * @author daniel
 *
 */
public class WinterwellProjectFinder implements IFn<String, File> {


	private static final String LOGTAG = "WinterwellProjectFinder";
	
	
	private static volatile boolean initFlag;

	public WinterwellProjectFinder() {
		init();
	}

	/**
	 * Just spit out some debug info (once)
	 */
	private void init() {
		if (initFlag) return;
		initFlag = true;
		// Where are we looking?
		File wdir = FileUtils.getWinterwellDir();
		Log.d("ProjectFinder", "WINTERWELL_HOME: "+wdir);		
		Log.d("ProjectFinder", "bobwarehouse: "+new File(wdir, "bobwarehouse"));
	}

	/**
	 * @param _projectName The name in Eclipse. Or (hack) the "main" name, and we have hardcoded mappings to eclipse projects
	 * @return null on failure
	 */
	@Override
	public File apply(String _projectName) {
		// HACK portal is in adserver
		if ("portal".equals(_projectName)) {
			_projectName = "adserver";
		}
		if ("taskeroo".equals(_projectName)) {
			_projectName = "mondaybot";
		}
		if ("mesaureengine".equals(_projectName)) {
			_projectName = "greendata";
		}		
		List<File> possDirs = new ArrayList();
		// are we in the project dir?
		if (FileUtils.getWorkingDirectory().getName().equals(_projectName)) {
			possDirs.add(FileUtils.getWorkingDirectory());
		}
		// ...also check the Eclipse project file
		try {
			// This will likely fail on a "strange" computer as it uses winterwell-home :(
			EclipseClasspath ec = new EclipseClasspath(FileUtils.getWorkingDirectory());
			String pname = ec.getProjectName();
			if (_projectName.equals(pname)) {
				possDirs.add(FileUtils.getWorkingDirectory());
			}
		} catch(Exception ex) {
			// oh well
		}
		
		try {
			File wdir = FileUtils.getWinterwellDir();
			// prefer the warehouse
			possDirs.add(new File(wdir, "bobwarehouse/"+_projectName));
			possDirs.add(new File(wdir, "bobwarehouse/open-code/"+_projectName));
			// NB: winterwell-code is typically cloned as code, so let's check both options
			possDirs.add(new File(wdir, "bobwarehouse/code/"+_projectName));
			possDirs.add(new File(wdir, "bobwarehouse/winterwell-code/"+_projectName));
			// local, sibling to current dir
			possDirs.add(new File("..", _projectName).getAbsoluteFile());
			// only the warehouse? For robustly repeatable builds
//			possDirs.add(new File(wdir, "open-code/"+_projectName));
//			possDirs.add(new File(wdir, "code/"+_projectName));
//			possDirs.add(new File(wdir, _projectName));
		} catch(Exception ex) {
			// no WINTERWELL_HOME
			Log.w(LOGTAG, "No WINTERWELL_HOME found "+ex);			
		}		
		// Bug seen May 2020: beware of basically empty dirs
		for (File pd : possDirs) {
			if ( ! pd.exists()) continue;
			// HACK look for some file to confirm its valid
			for(String f : "src .classpath .project config".split(" ")) {
				if (new File(pd, f).exists()) {
					return pd;
				}
			}
		}
		// failed
		Log.e(LOGTAG, "Could not find project directory for "+_projectName+" Tried "+possDirs);
		return null;
	}

	/**
	 * HACK for deploying WW libs
	 * {project-name: "repo_url repo_folder sub_folder"}
	 * 
	 * NB: use https repo urls for public repos, and git@github.com ssh info for private ones
	 */
	static final Map<String,String> KNOWN_PROJECTS = new ArrayMap(
		"winterwell.utils", 
			"https://github.com/good-loop/open-code open-code winterwell.utils",
		"winterwell.web", 
			"https://github.com/good-loop/open-code open-code winterwell.web",
		"winterwell.webappbase", 
			"https://github.com/good-loop/open-code open-code winterwell.webappbase",
		"winterwell.nlp", 
			"https://github.com/good-loop/open-code open-code winterwell.nlp",
		"winterwell.maths", 
			"https://github.com/good-loop/open-code open-code winterwell.maths",
		"winterwell.datalog", 
			"https://github.com/good-loop/open-code open-code winterwell.datalog",
		"winterwell.depot", 
			"https://github.com/good-loop/open-code open-code winterwell.depot",
		"winterwell.bob", 
			"https://github.com/good-loop/open-code open-code winterwell.bob",
		"youagain-java-client", 
			"https://github.com/good-loop/open-code open-code youagain-java-client",
			
		"adserver",
			"git@github.com:/good-loop/adserver",
		"calstat",
			"git@github.com:/good-loop/calstat",
		"elasticsearch-java-client",
			"https://github.com/good-loop/elasticsearch-java-client.git",
		"jerbil",
			"https://github.com/good-loop/jerbil",
		"juice",		
			"https://github.com/good-loop/juice",
		"play.good-loop.com",
			"https://github.com/good-loop/play.good-loop.com.git",
			
		"jtwitter",
			"https://github.com/winterstein/JTwitter.git",
		"flexi-gson", 
			"https://github.com/winterstein/flexi-gson.git",
			
		"dataloader",
			"git@github.com:/good-loop/code code dataloader",
		"rax",
			"git@github.com:/good-loop/rax",
		"youagain-server",
			"git@github.com:/good-loop/code code youagain-server",
		"winterwell.demographics",
			"git@github.com:/good-loop/code code winterwell.demographics"

	);


	private static final String DEVS = "3d0f3b9ddcacec30c4008c5e030e6c13a478cb4f 886fed01789257424228dc95fe3b5b319335ab6d e51e0366dbb789ed520ec6de35f106176865b05c 748e1641a368164906d4a0c0e3965345453dcc93 79069711144d44b4f370f4b55eb7351cb7917547 c3918f39022407dee6a0056ceca9b1aa80e8e4ce 17b9e1c64588c7fa6419b4d29dc1f4426279ba01 bdc6f8434ef1f9386b4f11352684390814ee550e bd6658dc079b66a2294520d850705b9aa350119d";

	public static boolean isDev(WebRequest state) {
		XId uxid = state.getUserId();
		if (uxid == null) return false;
		if ( ! uxid.getName().endsWith("@good-loop.com")) return false;
		String n = StrUtils.substring(uxid.getName(), 0, - "@good-loop.com".length());
		String sn = StrUtils.sha1(n);
		return DEVS.contains(sn);
	}
	

}

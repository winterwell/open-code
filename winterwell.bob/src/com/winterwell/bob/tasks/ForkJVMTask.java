package com.winterwell.bob.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.winterwell.bob.Bob;
import com.winterwell.bob.BobConfig;
import com.winterwell.bob.BuildTask;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.TUnit;


/**
 * This task runs a child Bob in a separate Java process
 * 
 * FIXME It does not preserve the file settings
 * Maybe send an xstream aml blob via a temp file??
 * 
 * @author daniel
 * @testedby ForkJVMTaskTest
 */
public class ForkJVMTask extends BuildTask {
	
	/**
	 * NB: String 'cos the class might not be on the classpath for this JVM
	 */
	final String target;
	
	public ForkJVMTask(Class<? extends BuildTask> target) {
		this(target.getName());
	}
	
	public ForkJVMTask() {
		this(""); // find it!
	}
	public ForkJVMTask(String target) {
		this.target = target;
		// odds are, you don't need to repeat these every time
		setSkipGap(TUnit.HOUR.dt);
	}
	
	Classpath classpath = Classpath.getSystemClasspath();
	
	public ForkJVMTask setDir(File dir) {
		this.dir = dir;
		return this;
	}
	
	/**
	 * working dir for task
	 */
	private File dir;
	private String mainClass = Bob.class.getCanonicalName();
	
	public Classpath getClasspath() {
		return classpath;
	}
	
	public void setClasspath(Classpath classpath) {
		this.classpath = classpath;
	}
	
	@Override
	protected void doTask() throws Exception {
		// TODO pass on Bob settings like -clean
		// BUT we dont want to rebuild utils n times in one build -- so use cleanBefore
		List<String> options = new ArrayList();
		
		// Java 17+ security
		double jv = ReflectionUtils.getJavaVersion();
		List<String> jvmOptions = new ArrayList();
		if (jv >= 17) {
			jvmOptions.add("--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.security=ALL-UNNAMED --add-opens java.base/sun.security.pkcs=ALL-UNNAMED");
		}
		
		BobConfig config = Bob.getSingleton().getConfig();
		if (config.cleanBefore != null) {
			options.add("-cleanBefore "+config.cleanBefore.getTime());
		} else if (config.clean) {
			options.add("-cleanBefore "+Bob.getRunStart().getTime());
		}
		if (config.ignoreAllExceptions) {
			options.add("-ignore "+config.ignoreAllExceptions);
		}
		options.add("-label "+config.label);
		options.add("-depth "+(config.depth+1));
		if (config.maxDepth!=null && config.maxDepth > 0) {
			options.add("-maxDepth "+config.maxDepth);
		}
		String soptions = StrUtils.join(options, " ")+" ";
		
		String command = "java -cp "+classpath+" "+StrUtils.join(jvmOptions, " ")+" "+mainClass+" "
				+soptions
				+target;
		Log.d(LOGTAG, "fork "+target+" Full command: "+command);
		
		// child call to Bob
		ProcessTask proc = null;
		try {
			proc = new ProcessTask(command);
			if (maxTime!=null) proc.setMaxTime(maxTime);
			proc.setDirectory(dir);
			proc.setEcho(true);
			Log.i(LOGTAG, "Child-Bob: "+proc.getCommand()+" \r\n[in dir "+dir+"]");
			proc.run();
			
			Log.d(LOGTAG, "Success: "+command+" [in dir "+dir+"]");

			// for debug - what did it try to build?
//			String out = proc.getOutput();
//			#bob Auto-build: found file /home/daniel/winterwell/open-code/winterwell.utils/builder/com/winterwell/bob/wwjobs/BuildUtils.java
			
			// maybe the child bob did some tasks for us :(
			Bob.loadTaskHistory();
			
		} finally {
			long pid = proc.getProcessID();		
			FileUtils.close(proc); // paranoia
			Log.d(LOGTAG, "closed process "+pid);			
		}		
	}
	
	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

}

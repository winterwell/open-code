package com.winterwell.bob.tasks;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import com.winterwell.bob.BuildTask;
import com.winterwell.utils.FailureException;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;

import junit.framework.TestCase;

/**
 * Class for creating a HTML JUnit test report.
 * 
 * This ONLY operates on {@link TestCase} classes. It does not cover all-unit-tests-in-a-folder.
 * Which is actually a useful feature for us.
 * 
 * @author Jacob Dreyer, released as public with permission to edit and use on
 *         http://www.velocityreviews.com/forums/t149403-junit-html-report.html
 * @author Daniel Winterstein - modifications to Jacob's code
 * @testedby  JUnitTaskTest
 */
public class JUnitTask extends BuildTask {

	private final Collection<File> classpath;
	private boolean exceptionOnTestFailure;

	private final File outputFile;
	private final File sourceDirectory;
	private transient JUnitTestHtmlReport report;
//	private transient File classDirectory;

	/**
	 * Create a HTML report instance. Typical usage:
	 * 
	 * <pre>
	 * File classDir = new File("/home/joe/dev/classes");
	 * JUnitTask unitTest = new JUnitTask(null, classDir, new File("tests.html"));
	 * unitTest.run();
	 * </pre>
	 * 
	 * @param sourceDirectory
	 *            Root directory of source. If specified, it will be used to
	 *            create a link to the source file of failing classes. May be
	 *            null.
	 * @param classDir
	 *            Root directory of test classes.
	 *            For *finding* tests. Tests are run in the current JVM with its classpath.
	 */
	public JUnitTask(File srcDir, File classDir, File outputFile) {
		this(srcDir, Arrays.asList(classDir), outputFile);
	}
	
	/**
	 * 
	 * @param srcDir
	 * @param classPath For *finding* tests. Tests are run in the current JVM with its classpath.
	 * @param outputFile
	 */
	public JUnitTask(File srcDir, Collection<File> classPath, File outputFile) {
		sourceDirectory = srcDir;
		classpath = classPath;
		this.outputFile = outputFile;
	}

	@Override
	public void doTask() throws Exception {
//		// Build temp dir of classes ??why??
//		classDirectory = FileUtils.createTempDir();
//		for(File f : classpath) {
//			FileUtils.copy(f, classDirectory, true);
//		}
		// Create a HTML report instance
		if (classpath.size() != 1) throw new TodoException();
		File classDirectory = Containers.first(classpath);
		report = new JUnitTestHtmlReport(sourceDirectory, classDirectory);
		report.setOutputFile(outputFile);
		outputFile.getParentFile().mkdirs();
		// Run the tests!
		report.print();
		// report
		Log.d(LOGTAG, "Tested " + classDirectory + ". " + getSuccessCount()
				+ " tests passed, " + getFailureCount() + " tests failed.");
		// Exception?
		if (exceptionOnTestFailure && report.getFailureCount() > 0) {
			throw new FailureException("junit",
					"Test failed (and JUnitTask is set to throw exceptions). See "
							+ outputFile + " for details.");
		}		
	}
	
	public void close() {
//		FileUtils.deleteDir(classDirectory);
	}

	/**
	 * @param exceptionOnTestFailure
	 *            If true, this task will throw an exception should any test
	 *            fail. Default: false. Useful for when the tests are crucial to
	 *            the next task (e.g. deploying an updated version).
	 */
	public void setExceptionOnTestFailure(boolean exceptionOnTestFailure) {
		this.exceptionOnTestFailure = exceptionOnTestFailure;
	}

	public int getSuccessCount() {
		return report.getSuccessCount();
	}

	public int getFailureCount() {	
		return report.getFailureCount();
	}

}

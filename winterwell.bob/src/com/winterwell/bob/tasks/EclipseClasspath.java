package com.winterwell.bob.tasks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Node;

import com.winterwell.utils.IFn;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.VersionString;
import com.winterwell.utils.WrappedException;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.ListMap;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.KErrorPolicy;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils;

/**
 * HACK Interrogate an Eclipse .classpath file (which is where
 * the dependencies for a Java project are kept).
 *
 * WARNING: Assumes the Winterwell directory layout!
 *
 * This relies on various assumptions about the workspace layout and specific files that may not be valid.
 *
 * @author Daniel
 * @testedby {@link EclipseClasspathTest}
 */
public class EclipseClasspath {

	@Override
	public String toString() {
		return "EclipseClasspath[projectDir=" + projectDir + "]";
	}

	/**
	 * the .classpath file
	 */
	private File file;
	private File projectDir;

	public File getProjectDir() {
		return projectDir;
	}

	/**
	 * the .classpath file
	 */
	public File getFile() {
		return file;
	}

	/**
	 *
	 * @param file Either the .classpath file or the project directory
	 * will do fine, thank you.
	 */
	public EclipseClasspath(File file) {
		assert file != null;
		if (file.isDirectory()) {
			file = new File(file, ".classpath");
		}
		this.file = file;
		this.projectDir = file.getParentFile();
		if ( ! file.exists()) {
			throw Utils.runtime(new FileNotFoundException(file.getAbsolutePath()));
		}
	}

	/**
	 * @return The jar files referenced by this project.
	 * This does NOT include jar files referenced by projects which this
	 * project references (i.e. it's not recursive -- see {@link #getCollectedLibs()}).
	 *
	 * The method for resolving workspace paths is far from foolproof. It
	 * assumes a flat workspace structure, where each project has a folder in
	 * the "workspace directory" (the default is set to [winterwell home]/code).
	 * @see #setWorkspace(File)
	 * @see #getCollectedLibs()
	 */
	public List<File> getReferencedLibraries() {
		String xml = FileUtils.read(file);
		List<Node> tags = WebUtils.xpathQuery("//classpathentry[@kind='lib']", xml);
		if (tags.isEmpty()) {
			Log.i("eclipse", "No classpath kind=lib tags found in "+file);
		}
		List<File> files = new ArrayList<File>();
		for (Node node : tags) {
			Node path = node.getAttributes().getNamedItem("path");
			File f = getFileFromEPath(path.getTextContent());
			files.add(f);
		}
		return files;
	}
	
	KErrorPolicy onError = KErrorPolicy.REPORT;
	
	public void setErrorPolicy(KErrorPolicy onError) {
		this.onError = onError;
	}
	

	private File getFileFromEPath(String path) {
		File f = getFileFromEPath2(path);
		if (f.exists()) return f;
		// fail!
		switch(onError) {
		case THROW_EXCEPTION: 
			throw new WrappedException(new FileNotFoundException(path));
		case REPORT:
			Log.e("eclipse.classpath", projectDir+" Cannot resolve file for path: "+path);
		case IGNORE: case ACCEPT:
			break;
		case DELETE_CAUSE: case RETURN_NULL: 
			return null;
		case ASK:
			throw new TodoException(path);			
		case DIE:
			Log.e("eclipse.classpath", "Cannot resolve file for path: "+path);
			System.exit(1);
		}
		return f;
	}
	
	

	private File getFileFromEPath2(String path) {
		if ( ! path.startsWith("/") || path.startsWith("\\")) {
			// Our project
			File f = new File(projectDir, path);		 
			return f;
		}
		// need to locate the damn project!
		String[] pathBits = path.split("[/\\\\]");
		// Drop the first bit (which wil be empty -- from the leading /)
		List<String> rest = Arrays.asList(Arrays.copyOfRange(pathBits, 2, pathBits.length));
		File pdir = projectFinder.apply(pathBits[1]);
		File f = new File(pdir, StrUtils.join(rest, "/"));
		return f;
	}

	/**
	 * Patterned after getReferencedProjects
	 * @return a list of Eclipse "user library" names
	 */
	public List<String> getUserLibraries() {
		String xml = FileUtils.read(file);
		List<String> result = new ArrayList<String>();
		List<Node> tags = WebUtils.xpathQuery("//classpathentry[@kind='con']", xml);
		for (Node node : tags) {
			Node pathNode = node.getAttributes().getNamedItem("path");
			String path = pathNode.getTextContent();
			if (!path.startsWith("org.eclipse.jdt.USER_LIBRARY")) continue;
			String[] bits = path.split("/", 2);
			assert bits.length == 2 : "Unexpected user library format in " + path;
			result.add(bits[1]);
		}
		return result;
	}

	/**
	 * Retrieve jars stored in the Winterwell eclipse user library dictionary in
	 * middleware/userlibraries.userlibraries
	 * @param name the short name of the user library e.g. "akka"
	 * @return The list of jars in that library
	 */
	public Set<File> getUserLibraryJars(String name) {
		File mware = projectFinder.apply("middleware");
		File userLibraries =  new File(mware, "userlibraries.userlibraries");
		String xml = FileUtils.read(userLibraries);
		HashSet<File> result = new HashSet<File>();
		// TODO: I suppose we should error out if the library is not defined!
		List<Node> tags = WebUtils.xpathQuery("//library[@name='" + name + "']/archive", xml);
		for (Node node : tags) {
			Node pathNode = node.getAttributes().getNamedItem("path");
			String path = pathNode.getTextContent();
			File f = getFileFromEPath(path);
			result.add(f);
		}
		return result;
	}
	
	IFn<String,File> projectFinder = new WinterwellProjectFinder();
	private boolean includeProjectJars;

	public IFn<String, File> getProjectFinder() {
		return projectFinder;
	}
	
	/**
	 * @return The Eclipse projects referenced by this project.
	 */
	public List<String> getReferencedProjects() {
		String xml = FileUtils.read(file);
		List<Node> tags = WebUtils.xpathQuery("//classpathentry[@kind='src']", xml);
		List<String> files = new ArrayList<String>();
		for (Node node : tags) {
			Node path = node.getAttributes().getNamedItem("path");
			String f = path.getTextContent();
			// local src folder?
			if ( ! (f.startsWith("/")
				|| f.startsWith("\\"))) continue;
			// need to locate the damn project!
			files.add(f.substring(1));
		}
		return files;
	}

	/**
	 * @return all the jar files needed? Never null.
	 * 
	 * This can get confused by jars from Eclipse user-libraries :(
	 */
	public Set<File> getCollectedLibs() {
		try {
			Set<File> libs = new HashSet();
			Set<String> projects = new HashSet();
			getCollectedLibs2(libs, projects, depsFor);
			return libs;
		} catch (Exception ex) {
			throw Utils.runtime(ex);
		}
	}
	
	/**
	 * dependency graph. uses jar file & project names
	 */
	ListMap<String,String> depsFor = new ListMap<>();
	
	public ListMap<String, String> getDepsFor() {
		return depsFor;
	}

	/**
	 * 
	 * @param libs
	 * @param projects Avoid repeats
	 * @param depsFor 
	 * @throws IOException 
	 */
	private void getCollectedLibs2(Set<File> libs, Set<String> projects, ListMap<String, String> depsFor) throws IOException
	{
		String pro = getProjectName();
		List<File> libs2 = getReferencedLibraries();
		for (File jfile : libs2) {
			if ( ! jfile.isFile()) {
				if (jfile.getName().endsWith("-sources.jar")) {
					Log.w(LOGTAG, "(skip source jar) "+this+" Missing referenced jar "+jfile);
					continue; // HACK: skip missing source code jars
				}
				throw new IOException(this+ " Referenced jar "+jfile+" does not exist.");
			}
			libs.add(jfile);	
		}
		libs.addAll(libs2);
		depsFor.put(pro, Containers.apply(libs2, File::getName));

		// User libraries
		for (String lib : getUserLibraries()) {
			Set<File> userLibraryJars = getUserLibraryJars(lib);
			for (File jfile : userLibraryJars) {
				if ( ! jfile.isFile()) {
					throw new IOException("UserLibraryJar "+jfile+" does not exist.");
				}
				libs.add(jfile);	
			}			
			depsFor.put(lib, Containers.apply(userLibraryJars, File::getName));
		}

		projects.add(pro);
		List<String> pros = getReferencedProjects();
		for (String p : pros) {
			depsFor.add(pro, p);
			if (projects.contains(p)) continue;			
			// prefer top level projects
			File fp = null;
			try {
				fp = projectFinder.apply(p);				
			} catch(Exception ex) {
				Log.w(LOGTAG, "Could not find project "+p+" for collecting libs: "+ex);
				continue;
			}
			if (fp==null || ! fp.exists()) {
				Log.i(LOGTAG, "Could not find project "+p+" for collecting libs");
				continue;
			}
			try {
				EclipseClasspath pec = new EclipseClasspath(fp);
				pec.setIncludeProjectJars(includeProjectJars);
				pec.getCollectedLibs2(libs, projects, depsFor);
			} catch(Exception ex) {
				Log.w("eclipse."+projectDir.getName(), ex);
			}
			// HACK add in the project jar?
			if (includeProjectJars) {
				File projectJar = new File(fp, p+".jar");
				if (projectJar.isFile()) {
					libs.add(projectJar);
					depsFor.add(pro, projectJar.getName());
				} else {
//					String dp = FileUtils.read(new File(fp,".project"));
//					if (dp.contains("maven")) {
					List<File> jars = FileUtils.find(new File(fp, "target"), ".*\\.jar");
//					new VersionString(dp)
					jars.sort((a,b) -> a.getName().compareTo(b.getName())); // which way does this sort??
					if ( ! jars.isEmpty()) {
						if (jars.size() > 1) {
							System.out.println(jars); // TODO check ordering
						}
						File mvnJar = jars.get(0);
						libs.add(mvnJar);
						depsFor.add(pro, mvnJar.getName());
					} else {
						Log.d(LOGTAG, "No project jar for "+p+" dir: "+fp);
					}
				}
			} else {
				Log.d(LOGTAG, "Dont include project jar for "+p);
			}
		}
	}
	
	static final String LOGTAG = "EclipseClasspath";

	public String getProjectName() {
		File dotProject = new File(file.getParentFile(),".project");
		String xml = FileUtils.read(dotProject);
		List<Node> tags = WebUtils.xpathQuery("//name", xml);
		return tags.get(0).getTextContent();
	}

	/**
	 * HACK method - if a referenced project has a jar named
	 * (project)(version numbers?).jar
	 * Then include that jar
	 */
	public void setIncludeProjectJars(boolean b) {
		includeProjectJars = b;		
	}

	public List<File> getSrcDirs() {
		// copy pasta from getRefProjects -- TODO merge
		String xml = FileUtils.read(file);
		List<Node> tags = WebUtils.xpathQuery("//classpathentry[@kind='src']", xml);
		List<File> files = new ArrayList();
		for (Node node : tags) {
			Node path = node.getAttributes().getNamedItem("path");
			String f = path.getTextContent();
			// not a local src folder?
			if (f.startsWith("/") || f.startsWith("\\")) continue;
			files.add(new File(projectDir, f));
		}
		return files;
	}

}

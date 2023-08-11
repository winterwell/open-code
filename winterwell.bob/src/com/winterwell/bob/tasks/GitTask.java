package com.winterwell.bob.tasks;

import java.io.File;
import java.util.Map;

import com.winterwell.utils.FailureException;
import com.winterwell.utils.Proc;
import com.winterwell.utils.ShellScript;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.time.Time;

/**
 * This is just a wrapper for calling git via the command line. You must have
 * git installed.
 *
 * @author daniel
 * @testedbuy {@link GitTaskTest}
 */
public class GitTask extends ProcessTask {

	/**
	 * Utility method: what branch are we on?
	 * @param dir Can be null for working directory
	 * @return branch name
	 */
	public static String getGitBranch(File dir) {
		// This command is unreliable, so try a few times with delays before failing
		String out = null;
		for(int tries=0; tries<4; tries++) {
			// Do we need to cd back afterwards??
			String here = new File("").getAbsolutePath();;
			
			Proc p;
			if (dir==null) {
				p = new Proc("git branch");
			} else {
				// run in another dir
				p = new ShellScript(
						"cd "+dir.getPath()+";\n"
						+"git branch;\n");				
			}
			p.run();
			Utils.sleep(50*tries);
			p.waitFor(2000);
			
			// FIXME delete
			String here2 = new File("").getAbsolutePath();;
			assert here.equals(here2);
			
			out = p.getOutput();
			String[] outlines = StrUtils.splitLines(out);
			for(String line : outlines) {
				if ( ! line.contains("*")) continue;
				String branch = line.replaceAll("\\*\\s", "");
				return branch;
			}	
			Utils.sleep(tries*10);
		}
		throw new FailureException(out);
	}
	
	public static String getLastCommitId(File dir) {
		String command = "git log -1 --format=%h"; // use %H for full commit ID, but shorts should always be good enough
		
		Proc p;
		if (dir==null) {
			p = new Proc(command);
		} else {
			// run in another dir
			p = new ShellScript(
					"cd "+dir.getPath()+";\n"
					+command + "\n");				
		}
		p.run();
		p.waitFor(2000);	
		
		String out = p.getOutput();
		// TODO: Check for hex string
		
		return out.trim();
	}
	
	
	/**
	 * 
	 * @param dir
	 * @return {hash, author, time, subject, branch}
	 * @throws IllegalArgumentException if dir is not a git-managed directory
	 */
	public static Map<String,Object> getLastCommitInfo(File dir) throws IllegalArgumentException {
		String SEP=">   <";
		// https://www.git-scm.com/docs/git-log
		String command = "git log -1 --format=\"%H"+SEP+"%an"+SEP+"%aI"+SEP+"%s"+SEP+"%D\"";		
		
		Proc p;
		if (dir==null) {
			p = new Proc(command);
		} else {
			// run in another dir
			p = new ShellScript(
					"cd "+dir.getPath()+";\n"
					+command + "\n");				
		}
		p.run();
		p.waitFor(2000);	
		
		String out = p.getOutput();
		if (Utils.isBlank(out)) {
			throw new IllegalArgumentException(dir+" is not a git directory");
		}
		String[] bits = out.split(SEP);
		String branch = bits[4].trim();
		// simplify the branch info
		String[] bbits = branch.split(", ");
		for (String bbit : bbits) {
			if (bbit.startsWith("origin/")) {
				String bbit2 = bbit.substring(7);
				if (bbit2.startsWith("HEAD")) continue;
				branch = bbit2;
				break;
			}
			if (bbit.contains("->")) {
				String bbit2 = bbit.split("->")[1].trim();
				if (bbit2.startsWith("HEAD")) continue;
				branch = bbit2;
			}
		}
		
		return new ArrayMap(
			"hash", bits[0],
			"author", bits[1],
			"time", new Time(bits[2]),
			"subject", bits[3].trim(),
			"branch", branch
		);		
	}
	
	
	public static final String COMMIT = "commit";
	public static final String COMMIT_ALL = "commit -a";
	/**
	 * A slightly more robust version of pull, which can avoid some "artificial" conflicts
	 * See https://gitolite.com/git-pull--rebase
	 */
	public static final String PULL_REBASE = "pull --rebase";
	public static final String PULL = "pull";
	public static final String STASH = "stash";
	public static final String PUSH = "push";
	public static final String ADD = "add";
	public static final String CLONE = "clone";
	public static final String RESET = "reset";
	public static final String GC = "gc";
	public static final String CHECKOUT = "checkout";

	private final String action;

	public GitTask(String action, File target) {
		super("git ");
		this.action = action;
//		addArg("--non-interactive");
		addArg(action);
		File targetDir = target.isDirectory()? target.getAbsoluteFile() : target.getAbsoluteFile().getParentFile();
		setWorkingDir(targetDir);		
		if (action.equals(COMMIT) || action.equals(ADD)) {
			setEndOfCommand(target.getName());
		}
	}


	/**
	 * Set the commit message
	 *
	 * @param message
	 *            E.g. "Hello World"
	 */
	public void setMessage(String message) {
		assert action.equals(GitTask.COMMIT) || action.equals(COMMIT_ALL) : action;
		// Switch quotes
		message = message.replace('"', '\'');
		addArg("-m \"" + message + '"');
	}

}

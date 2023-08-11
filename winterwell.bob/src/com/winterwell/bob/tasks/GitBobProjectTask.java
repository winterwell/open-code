package com.winterwell.bob.tasks;

import java.io.File;
import java.io.IOException;

import com.winterwell.bob.BuildTask;
import com.winterwell.bob.wwjobs.BuildHacks;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.TUnit;
import com.winterwell.web.app.KServerType;
/**
 * @tested {@link GitBobProjectTaskTest}
 * @author daniel
 *
 */
public class GitBobProjectTask extends BuildTask {

	@Override
	public String toString() {
		return "GitBobProjectTask [gitUrl=" + gitUrl + ", dir=" + dir + ", projectSubDir=" + projectSubDir + "]";
	}

	/**
	 * As per .git/config -- e.g. git@github.com:good-loop/open-code
	 */
	String gitUrl;
	

	/**
	 * target (local) directory
	 */
	File dir;
	
	/**
	 * As per .git/config -- e.g. git@github.com:good-loop/open-code
	 * target (local) directory
	 */
	public GitBobProjectTask(String gitUrl, File dir) {
		this.gitUrl = gitUrl;
		this.dir = dir;
		// dependencies shouldnt need rebuilding all the time
		setSkipGap(TUnit.WEEK.dt);
		resetLocalChanges = BuildHacks.getServerType() != KServerType.LOCAL;
	}
	
	/**
	 * If the project is a sub-dir of the main repo dir. Usually null
	 */
	File projectSubDir;

	boolean stashLocalChanges;
	/**
	 * This is set true for non-local. It helps ensure the git pull will work.
	 * It does delete local edits!
	 * 
	 * For local: beware of bobwarehouse vs "top-level" checkouts!
	 * Best practice is to symlink winterwell/open-code, etc into winterwell/bobwarehouse
	 * 
	 */
	boolean resetLocalChanges;
	
	@Override
	protected void doTask() throws Exception {
		Log.d(LOGTAG, dir+" < "+this);
		// clone or pull
		doTask2_cloneOrPull();
		
		// build				
		ForkJVMTask childBob = new ForkJVMTask();
		// HACK adserver has two build files so we have to specify one
		if ("adserver".equals(dir.getName())) {
			childBob.close();
			childBob = new ForkJVMTask("BuildAdserver");
		}
		childBob.setDepth(getDepth()+1);
		File pd;
		if (projectSubDir!=null) {			
			pd = projectSubDir;
		} else {
			pd = dir;
		}
		childBob.setDir(pd);
		// Do it!
		childBob.run();
		childBob.close();
	}

	private void doTask2_cloneOrPull() throws Exception, IOException {
		// it exists? then (reset and) pull
		if (dir.isDirectory() && new File(dir, ".git").isDirectory()) {
			// stash?
			if (stashLocalChanges) {
				GitTask gt0 = new GitTask(GitTask.STASH, dir);
				gt0.setDepth(getDepth()+1);				
				gt0.run();
				gt0.close();
			}		
			// reset first? a harder version of stash!
			if (resetLocalChanges) {
				// Garbage Collect the local repository -- helps setup resetting and pulling later
				Log.d(LOGTAG, "git gc --prune=now (because not a local dev box)");
				GitTask gc = new GitTask(GitTask.GC, dir);
				gc.addArg("--prune=now");
				gc.run();
				gc.close();
				// Pull hashes from git server -- regardless of the cleanliness of this pull task, it is 100% necessary before a reset can be accomplished
				Log.d(LOGTAG, "git pull origin (because not a local dev box)");
				GitTask gt = new GitTask(GitTask.PULL, dir);
				gt.addArg("origin");
				gt.run();
				gt.close();
				// Reset: Inform the local repository that it should only care about the files/hashes/changes which exist on the canonical git server
				Log.d(LOGTAG, "git reset --hard FETCH_HEAD (because not a local dev box)");
				GitTask gr = new GitTask(GitTask.RESET, dir);
				gr.addArg("--hard FETCH_HEAD");
				gr.run();
				gr.close();
				// Weird git bugs seen Nov 21 with the server somehow merging master into the branch
//				// Perform what seem to be arbitrary or inert 'checkout' and 'pull' commands, but actually, these help reset a local repository for future incoming commands. Weird, right?
//				Log.d(LOGTAG, "git checkout -f master (because not a local dev box)");
//				GitTask gco = new GitTask(GitTask.CHECKOUT, dir);
//				gco.addArg("-f master");
//				gco.run();
//				gco.close();
				Log.d(LOGTAG, "git pull (because not a local dev box)"); // is this needed??
				GitTask gp = new GitTask(GitTask.PULL, dir);
				gp.run();
				gp.close();
			}
			// pull
			try {
				GitTask gt = new GitTask(GitTask.PULL, dir);
				gt.setDepth(getDepth()+1);
				gt.run();
				gt.close();
			} catch (Exception ex) {
				if (BuildHacks.getServerType() == KServerType.LOCAL && ex.toString().contains("no tracking information")) {
					Log.d(LOGTAG, "(skip git pull) "+ex);
				} else {
					throw ex;
				}
			}
			return;
		}
		assert ! dir.isFile() : dir;
		
		// clone
		boolean ok = dir.getAbsoluteFile().mkdirs();
		if ( ! dir.isDirectory()) {
			throw new IOException("Could not make directory "+dir);
		}
		GitTask gt = new GitTask(GitTask.CLONE, dir);
		gt.setDepth(getDepth()+1);
		gt.addArg("--depth 1");
		gt.addArg(gitUrl);
		gt.addArg(dir.getAbsolutePath());
		gt.run();
		gt.close();		
	}

	public GitBobProjectTask setSubDir(String subdir) {
		projectSubDir = new File(dir, subdir.toString());
		return this;
	}

	/**
	 * HACK github details for core WW / GL projects
	 * @param pname
	 * @return
	 */
	public static GitBobProjectTask getKnownProject(String pname) {
		String g_s = WinterwellProjectFinder.KNOWN_PROJECTS.get(pname);
		if (g_s==null) return null;
		String[] gs = g_s.split(" ");
		boolean isSubdir = gs.length > 1; 
		File bobdir = getConfig().getGitBobDir();
		File dir = new File(bobdir, isSubdir? gs[1] : pname);
		GitBobProjectTask gb = new GitBobProjectTask(gs[0], dir);
		if (isSubdir) {
			gb.setSubDir(gs[2]);
		}
		return gb;
	}

}

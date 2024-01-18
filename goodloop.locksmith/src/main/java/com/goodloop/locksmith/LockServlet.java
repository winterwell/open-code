package com.goodloop.locksmith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.winterwell.data.KStatus;
import com.winterwell.utils.Dep;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.ajax.JThing;
import com.winterwell.web.app.IServlet;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.data.XId;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.YouAgainClient;

/**
 * Lets users lock editing of items so that accidental concurrent edits aren't made,
 * also allows users to request to edit at the same time and to accept other users.
 * N.B: Currently only locked on the front end, does not lock in the back-end - change??
 * @author vdurkin
 *
 */
public class LockServlet implements IServlet {

	public static PathLock pathTree;

	private static final int LOCK_USER_TIMEOUT_MILLISECS = 10000; // 30 seconds TODO: Change
	private static final int LOCK_PENDING_TIMEOUT_MILLISECS = 5000; // 10 seconds TODO: Change

	public LockServlet () {
		if (pathTree == null) {
			pathTree = new PathLock("root");
			pathTree.createChild("data");
			pathTree.createChild("draft");
		}
	}

	@Override
	public void process(WebRequest state) throws Exception {

		WebUtils2.CORS(state, false);

		YouAgainClient ya = Dep.get(YouAgainClient.class);
		List<AuthToken> tokens = ya.getAuthTokens(state);

		Log.d("----- USER -----");
		Log.d(state.getUser());

		XId username = state.getUserId();
		if (username == null) throw new WebEx.E403("No good-loop identity found");

		// TODO doSecurityCheck(state);
		String[] path = getPathFromSlug(state);
		// trim page slug
		path = Arrays.copyOfRange(path, 1, path.length);

		String op = path[0];
		// DEBUG
		if (op.equals("debug")) {
			JSend jsend = new JSend(pathTree);
			jsend.send(state);
			return;
		}
		// END DEBUG
		// trim var off path
		path = Arrays.copyOfRange(path, 1, path.length);

		String dataspace = path[0];
		// trim var off path
		path = Arrays.copyOfRange(path, 1, path.length);

		PathLock pathRoot = pathTree.getChild(dataspace);
		if (pathRoot == null) throw new WebEx.E400("Bad dataspace: " + dataspace);

		// slightly weird writing, to make sure the lock variable remains effectively final
		PathLock fetchedLock = pathRoot.getPathLock(path);
		PathLock lock = fetchedLock == null ? pathRoot.createPathLock(path) : fetchedLock;

		if (op.equals("list")) {
			if (lock.users.containsKey(username)) {
				lock.renewUser(username);
			}
			Map<String, Object> params = state.getParameterMap();
			if (!params.containsKey("timeout") || !params.containsKey("data")) {
				throw new WebEx.E400("Long poll request needs a timeout and data param!");
			}
			int timeout = Integer.parseInt(((String[]) params.get("timeout"))[0]);
			String[] current = (String[]) params.get("data");
			JThing<LockUserInfo> currentJson = new JThing<>(current[0]);
			currentJson.setType(LockUserInfo.class);
			LockUserInfo compare = currentJson.java();

			// Give thread timeout, so it doesn't stick around and cause server to clog up
			ExecutorService executor = Executors.newSingleThreadExecutor();
			Future future = executor.submit(() -> {
				synchronized (lock) {
					LockUserInfo userInfo = lock.getUserInfo();
					if (compare.users.containsAll(userInfo.users) && compare.users.size() == userInfo.users.size()
								&& compare.pendingUsers.containsAll(userInfo.pendingUsers) && compare.pendingUsers.size() == userInfo.pendingUsers.size()) {
						// No change - wait for an update
						Log.d("Thread waiting.......");
						try {
							lock.wait();
						} catch (InterruptedException e) {
							// do nothing
						}
						// Lock notified! Update
					}
					// If comparison is different, send immediate update
					JSend jsend = new JSend(lock.getUserInfo()); // Don't send previously fetched userInfo - might be out of date
					jsend.send(state);
				}
			});
			try {
				future.get(timeout, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				Log.d("Thread timed out!");
			}

			executor.shutdownNow();
		} else if (op.equals("lock")) {
			synchronized (lock) {
				Log.d("I AM PERFORMING A LOCK OPERATION");
				Log.d("HERE I GO");
				LockResponse response = pathRoot.lockPath(path, username);
				lock.notifyAll();
				JSend jsend = new JSend(response);
				jsend.send(state);
				Log.d("WOW WHAT A TIME");
			}
		} else if (op.equals("check")) {
			LockResponse response = new LockResponse();
			if (lock.isAttached(username)) {
				response.success = true;
			} else if (lock.isPending(username)) {
				response.requested = true;
			}
			JSend jsend = new JSend(response);
			jsend.send(state);
		} else if (op.equals("accept")) {
			LockResponse response = new LockResponse();
			if (lock.isAttached(username)) {
				Map<String, Object> params = state.getParameterMap();
				String userAccept = (String) params.get("user");
				response.success = lock.acceptPendingUser(userAccept);
			}
			JSend jsend = new JSend(response);
			jsend.send(state);
		} else if (op.equals("release")) {
			LockResponse response = new LockResponse(true, false);
			synchronized (lock) {
				lock.clearUser(username);
				lock.notifyAll();
				JSend jsend = new JSend(response);
				jsend.send(state);
			}
		}

	}

	protected String[] getPathFromSlug (WebRequest state) {
		String slug = state.getSlug();
		String[] slugBits = state.getSlugBits();
		// Path should be directly encoded as path/to/value
		return slugBits;
	}

	public boolean isEditLocked (String[] path, XId user) {
		PathLock lock = pathTree.getPathLock(path);
		if (lock == null) return false;
		return lock.isAttached(user);
	}

	public boolean isEditLocked (KStatus status, Class type, String id, XId user) {
		String typeStr = type.toString();
		String dataspace = null;
		if (status == KStatus.DRAFT) dataspace = "draft";
		else if (status == KStatus.PUBLISHED) dataspace = "data";
		return isEditLocked(new String[] {dataspace, typeStr, id}, user);
	}

	public class EditLockException extends Exception {
		public EditLockException () {
			super("Field is edit locked by other user(s), cannot edit!");
		}
	}

	class PathLock {

		protected String name;
		protected HashMap<XId, Long> users = new HashMap<XId, Long>();
		protected HashMap<XId, Long> pendingUsers = new HashMap<XId, Long>();
		protected ArrayList<PathLock> children = new ArrayList<PathLock>();

		public PathLock (String name) {
			this.name = name;
		}

		public boolean isLocked () {
			checkUsers();
			return getUsers().size() > 0;
		}

		/**
		 * Create a new child from this node
		 * @param name
		 * @return
		 */
		public PathLock createChild (String name) {
			PathLock child = getChild(name);
			if (child == null) {
				child = new PathLock(name);
				children.add(child);
			}
			return child;
		}

		/**
		 * Create children to match a path from this node
		 * @param path
		 * @return
		 */
		public PathLock createPathLock (String[] path) {
			if (path.length == 0) return this;
			PathLock child = createChild(path[0]);
			String[] newPath = Arrays.copyOfRange(path, 1, path.length);
			return child.createPathLock(newPath);
		}

		/**
		 * Get a child by name
		 * @param name
		 * @return
		 */
		public PathLock getChild (String name) {
			for (PathLock child : children) {
				if (child.name.equals(name)) return child;
			}
			return null;
		}

		/**
		 * Get a PathLock by a string path from this node
		 * @param path
		 * @return
		 */
		public PathLock getPathLock (String[] path) {
			PathLock child = getChild(path[0]);
			if (path.length == 1 || child == null) {
				return child;
			} else {
				return child.getPathLock(Arrays.copyOfRange(path, 1, path.length));
			}
		}

		/**
		 * Attempt to lock a path from this node
		 * @param path
		 * @return true if locked, false if requested
		 */
		public LockResponse lockPath (String[] path, XId user) {
			checkUsers();
			if (path.length == 0) {
				// If this user is already attached, renew the lock
				if (isAttached(user)) {
					renewUser(user);
					return new LockResponse(true, false);
				} else if (!isLocked()) {
					// attach if possible, request if not
					attachUser(user);
					return new LockResponse(true, false);
				} else {
					pendUser(user);
					return new LockResponse(false, true);
				}
			} else {
				// if we're locked at a higher level, we can't let others lock out below us
				if (users.size() == 0) {
					// head on down
					PathLock child = getChild(path[0]);
					return child.lockPath(Arrays.copyOfRange(path, 1, path.length), user);
				} else return new LockResponse(false, false);
			}
		}

		/**
		 * Get any attached users at or below this node
		 * @return
		 */
		public HashSet<XId> getUsers () {
			checkUsers();
			HashSet<XId> u = new HashSet<XId>();
			u.addAll(users.keySet());
			for (PathLock child : children) {
				u.addAll(child.getUsers());
			}
			return u;
		}

		/**
		 * Get user info in a web-friendly format
		 */
		public LockUserInfo getUserInfo () {
			checkUsers();
			return new LockUserInfo(users.keySet(), pendingUsers.keySet());
		}

		/**
		 * Attach a user to this lock
		 * @param user
		 */
		public void attachUser (XId user) {
			users.put(user, System.currentTimeMillis());
		}

		/**
		 * Remove a user from this lock
		 * @param user
		 */
		public void detachUser (XId user) {
			users.remove(user);
		}

		/**
		 * Renew a user's timer for being kicked out
		 * @param user
		 */
		public boolean renewUser (XId user) {
			if (!isAttached(user)) return false;
			users.put(user, System.currentTimeMillis());
			return true;
		}

		/**
		 * Check the user's timeouts.
		 * @param user
		 * @return
		 */
		public void checkUsers () {
			for (XId user : users.keySet()) {
				if (users.get(user) + LockServlet.LOCK_USER_TIMEOUT_MILLISECS < System.currentTimeMillis()) {
					clearUser(user);
				}
			}
			for (XId user : pendingUsers.keySet()) {
				if (pendingUsers.get(user) + LockServlet.LOCK_PENDING_TIMEOUT_MILLISECS < System.currentTimeMillis()) {
					rejectPendingUser(user);
				}
			}
		}

		/**
		 * Remove a user from this and any child locks
		 * @param user
		 */
		public void clearUser (XId user) {
			detachUser(user);
			for (PathLock child : children) {
				child.clearUser(user);
			}
			// If no one is left in the lock, clear all pending invites (to prevent race condition)
			if (!isLocked()) {
				pendingUsers.clear();
			}
		}

		/**
		 * Initiate a request for another user to edit
		 * @param user
		 */
		public void pendUser (XId user) {
			pendingUsers.put(user, System.currentTimeMillis());
		}

		/**
		 * Let another user into the lock
		 * @param user
		 * @return
		 */
		public boolean acceptPendingUser (String user) {
			checkUsers();
			XId toRemove = null;
			for (XId u : pendingUsers.keySet()) {
				if (u.getName() == user) { 
					toRemove = u;
					users.put(u, System.currentTimeMillis());
				}
			}
			if (toRemove != null) {
				pendingUsers.remove(toRemove);
				return true;
			}
			return false;
		}

		/**
		 * Reject a user's request to enter the lock
		 * @param user
		 */
		public void rejectPendingUser (XId user) {
			pendingUsers.remove(user);
		}

		/**
		 * Get all children of this node
		 * @return
		 */
		public ArrayList<PathLock> getChildren () {
			return children;
		}

		public boolean isPending (XId user) {
			checkUsers();
			return pendingUsers.containsKey(user);
		}

		public boolean isAttached (XId user) {
			checkUsers();
			return users.containsKey(user);
		}

	}

	class LockResponse {

		public boolean success = false;
		public boolean requested = false;

		public LockResponse () {}

		public LockResponse (boolean success, boolean requested) {
			this.success = success;
			this.requested = requested;
		}

	}

	class LockUserInfo {
		public Set<XId> users;
		public Set<XId> pendingUsers;

		public LockUserInfo (Set<XId> users, Set<XId> pendingUsers) {
			this.users = users;
			this.pendingUsers = pendingUsers;
		}

		// For JThing parsing
		public LockUserInfo (String[] userStr, String[] pendingUserStr) {
			for (String str : userStr) {
				users.add(new XId(str));
			}
			for (String str : pendingUserStr) {
				pendingUsers.add(new XId(str));
			}
		}
	}
}

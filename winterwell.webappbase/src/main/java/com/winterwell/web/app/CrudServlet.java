package com.winterwell.web.app;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.winterwell.bob.tasks.GitTask;
import com.winterwell.bob.wwjobs.BuildHacks;
import com.winterwell.data.AThing;
import com.winterwell.data.ISecurityByShares;
import com.winterwell.data.KStatus;
import com.winterwell.depot.IInit;
import com.winterwell.es.ESPath;
import com.winterwell.es.IESRouter;
import com.winterwell.es.client.DeleteRequest;
import com.winterwell.es.client.ESHit;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.IESResponse;
import com.winterwell.es.client.KRefresh;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.es.client.query.BoolQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilder;
import com.winterwell.es.client.query.ESQueryBuilders;
import com.winterwell.es.client.sort.KSortOrder;
import com.winterwell.es.client.sort.Sort;
import com.winterwell.gson.FlexiGson;
import com.winterwell.gson.Gson;
import com.winterwell.gson.GsonBuilder;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Dep;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.ArraySet;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.io.CSVSpec;
import com.winterwell.utils.io.CSVWriter;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.time.Period;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.web.JsonPatch;
import com.winterwell.utils.web.JsonPatchOp;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.WebEx;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.ajax.JThing;
import com.winterwell.web.ajax.JsonResponse;
import com.winterwell.web.app.WebRequest.KResponseType;
import com.winterwell.web.data.IHasXId;
import com.winterwell.web.data.XId;
import com.winterwell.web.fields.Checkbox;
import com.winterwell.web.fields.IntField;
import com.winterwell.web.fields.JsonField;
import com.winterwell.web.fields.ListField;
import com.winterwell.web.fields.SField;
import com.winterwell.youagain.client.AuthToken;
import com.winterwell.youagain.client.NoAuthException;
import com.winterwell.youagain.client.YouAgainClient;
/**
 * TODO security checks
 *  
 * @author daniel
 *
 * @param <T>
 */
public abstract class CrudServlet<T> implements IServlet {


	/**
	 * HACK allow Impact Hub requests to get lists of published stuff
	 * @param state
	 * @return true if access=public was passed in
	 * @throws E403 if status=DRAFT and access=public
	 */
	protected boolean isPublicAccess(WebRequest state) {
		boolean publicAccess = "public".equals(state.get("access"));
		if (publicAccess) {
			if (state.get(CommonFields.STATUS) != KStatus.PUBLISHED) {
				throw new WebEx.E403("public access is only with status=PUBLISHED");
			}
		}
		return publicAccess;
	}

	/**
	 * File path IF this is backed by a git repo (most servlets aren't)
	 * @param item
	 * @param status
	 * @return
	 */
	protected File getGitFile(AThing item, KStatus status) {
		// TODO a config setting
		File dir = new File(FileUtils.getWinterwellDir(), AMain.appName+"-files");
		// HACK backwards compatability
		if (AMain.main.getAppNameLocal().contains("moneyscript")) {
			dir = new File(FileUtils.getWinterwellDir(), AMain.appName+"-plans");
		}
		if ( ! dir.isDirectory()) {
			return null;
		}
		String wart = "";
		if (status==KStatus.DRAFT || status==KStatus.MODIFIED) wart = "~";
		String safename = FileUtils.safeFilename(wart+item.getId(), false);
		File f = new File(dir, item.getClass().getSimpleName()+"/"+safename);
		if ( ! f.getParentFile().isDirectory()) {
			f.getParentFile().mkdirs(); // create the repo/Type folder if needed
		}
		return f;
	}

	
	
	
	protected String[] prefixFields = new String[] {"name"};
	
	protected boolean dataspaceFromPath;
	public static final String ACTION_PUBLISH = "publish";
	public static final String ACTION_NEW = "new";
	/**
	 * get, or create if absent
	 */
	public static final String ACTION_GETORNEW = "getornew";
	public static final String ACTION_SAVE = "save";
	public static final String ACTION_DELETE = "delete";

	public CrudServlet(Class<T> type) {
		this(type, Dep.get(IESRouter.class));
	}
	
	
	public CrudServlet(Class<T> type, IESRouter esRouter) {
		this.type = type;
		this.esRouter = esRouter;
		Utils.check4null(type, esRouter);
	}

	protected JThing<T> doDiscardEdits(WebRequest state) {
		ESPath path = esRouter.getPath(dataspace, type, getId(state), KStatus.DRAFT);
		DeleteRequest del = es().prepareDelete(path.index(), path.type, path.id);
		IESResponse ok = del.get().check();		
		getThing(state);
		return jthing;
	}

	public void process(WebRequest state) throws Exception {
		// CORS??
		WebUtils2.CORS(state, false);
		
		// dataspace?
		if (dataspaceFromPath) {
			String ds = state.getSlugBits(1);
			setDataspace(ds);
		}
		
		doSecurityCheck(state);
		
		// list?
		String slug = state.getSlug();
		if (isListRequest(slug)) {
			doList(state);
			return;
		}
		if (slug.endsWith("/_stats") || "_stats".equals(slug)) {
			doStats(state);
			return;
		}
		
		// crud?
		// HACK: /new => action=new
		if (slug.endsWith("/new") && state.getAction()==null) {
			state.setAction(ACTION_NEW);
		}
		if (state.getAction() != null && ! state.actionIs("get")) {
			// do it
			doAction(state);
		}						

		getThingStateOrDB(state);
	
		if (jthing != null) {
			if (state.getAction() == ACTION_NEW) {
				returnJson(state, "id");
			} else {
				returnJson(state);
			}
			return;
		}
		
		// no object...
		// return blank / messages
		if (state.getAction()==null) {
			// no thing? return a 404
			ESPath path = getPath(state);
			throw new WebEx.E404(state.getRequestUrl(), "Not found: "+path);
		}
		JsonResponse output = new JsonResponse(state);
		WebUtils2.sendJson(output, state);

		// post-return action? usually not
		if (state.getAction() != null && ! state.actionIs("get")) {
			postProcessAction(state);
		}
	}
	
	protected void returnJson(WebRequest state, String key) throws IOException {
		Map json = new ArrayMap<String, String>();
		json.put("id", jthing.map().get(key));
		String jsonString = Gson.toJSON(json);
		WebUtils2.sendJson(state, jsonString);
		return;
	}
	
	protected void returnJson(WebRequest state) throws IOException {
		// security filter?
		List<ESHit<T>> hit = Arrays.asList(new ESHit(jthing));
		YouAgainClient yac = Dep.get(YouAgainClient.class);
		List<AuthToken> tokens = yac.getAuthTokens(state);
		List<ESHit<T>> hitSafe = doList2_securityFilter(hit, state, tokens, yac);
		if (hitSafe.isEmpty()) {
			throw new WebEx.E403("User " + state.getUserId() + " cannot access " + state.getSlug());
		}
		// privacy: potentially filter some stuff from the json!
		JThing<T> cleansed = cleanse(jthing, state);
		// Editor safety
		if (cleansed != null) {
			cleanse2_dontConfuseEditors(cleansed, jthing, state);
		} else {
			cleansed = jthing; // never null
		}
		// augment? 
		if (augmentFlag) {
			JThing<T> aug = augment(cleansed, state);
			if (aug != null) {
				cleansed = aug;
			}
		}
		String json = cleansed.string();
		// pretty?
		if (state.get(new Checkbox("pretty"))) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Object jobj = gson.fromJson(json);
			String prettyJson = gson.toJson(jobj);
			json = prettyJson;
		}
		JSend jsend = new JSend<>();
		jsend.setData(new JThing<>().setJson(json));
		jsend.send(state);
//		JsonResponse output = new JsonResponse(state).setCargoJson(json);
//		WebUtils2.sendJson(output, state);
	}

	protected boolean isListRequest(String slug) {
		return slug.endsWith("/_list") || LIST_SLUG.equals(slug);
	}


	/**
	 * Called after the servlet has returned a response. Usually does nothing.
	 * Override to do slower tasks.
	 * @param state
	 */
	protected void postProcessAction(WebRequest state) {
		
	}


	/**
	 * 
	 * @param cleansed This may be modified
	 * @param unclean
	 * @param state
	 */
	private void cleanse2_dontConfuseEditors(JThing<T> cleansed, JThing<T> unclean, WebRequest state) {
		if (cleansed==unclean) {
			Log.e(LOGTAG(), "cleansed == unclean -- Should copy before cleaning");
			return;
		}
		// reinstate everything that was sent (to avoid confusing editors)
		String json = getJson(state);
		if (json == null) {
			return;					
		}		
		// We don't want to cleanse data that was sent in during an edit -- as that could be misinterpreted
		// by the client (who would then lose that data -- and maybe then save it)
		Map uncleanMap = unclean.map();

		Map sentMap = new JThing(json).map();
		Map<String, Object> cleanMap = cleansed.map();
		JsonPatch jpo = new JsonPatch(cleanMap, uncleanMap);			
		if (jpo.getDiffs().isEmpty()) {
			return;
		}
		JsonPatch jps = new JsonPatch(cleanMap, sentMap);
		// which diffs are the same?
		List<JsonPatchOp> sameDiffs = new ArrayList(jps.getDiffs());
		sameDiffs.retainAll(jpo.getDiffs());
		// apply
		JsonPatch jp2 = new JsonPatch(sameDiffs);
		HashMap safeMap = new HashMap(cleanMap);
		jp2.apply(safeMap);
		cleansed.setMap(safeMap);		
	}


	/** Can unauthorised users perform read-only operations on this servlet? */
	protected boolean canReadWithoutAuth() {
		return true;
	}

	/** Error message to attach to the HTTP 401 when an unauthenticated user tries to something they shouldn't */
	protected String noAuthErrorMsg(Object state) {
		return state.toString();
	}


	/**
	 * A very simple check! Are you logged in for e.g. publish?
	 * 
	 * Note: see also {@link #doList2_securityFilter(List, WebRequest)}
	 * @param state
	 * @throws SecurityException
	 */
	protected void doSecurityCheck(WebRequest state) throws SecurityException {
		YouAgainClient ya = Dep.get(YouAgainClient.class);
		List<AuthToken> tokens = ya.getAuthTokens(state);

		boolean actionReadOnly = (state.getAction() == null || state.actionIs("get") || isListRequest(state.getSlug()));

		if (actionReadOnly) {
			if (canReadWithoutAuth()) return;
		} else if (tokens.stream().anyMatch(t -> t.getXId().isService("pseudo")) ) {
			// Prevent guest users making edits under shared pseudo-login
			throw new WebEx.E403("Authenticated as read-only user.");
		}

		// logged in?					
		if (Utils.isEmpty(tokens)) {
			Log.w("crud", "No auth tokens for "+this+" "+state+" All JWT: "+ya.getAllJWTTokens(state));
			throw new NoAuthException(noAuthErrorMsg(state));
		}
	}

	/**
	 * ES takes 1 second to update by default, so save actions within a second could
	 * cause an issue. Allow an extra second to be safe.
	 */
	static final Cache<String,Boolean> ANTI_OVERLAPPING_EDITS_CACHE = CacheBuilder.newBuilder()
			.expireAfterWrite(2, TimeUnit.SECONDS)
			.build();
	private static final Checkbox ALLOW_OVERLAPPING_EDITS = new Checkbox("allowOverlappingEdits");
	
	protected void doAction(WebRequest state) throws Exception {
		// Defend against repeat calls from the front end
		doAction2_blockRepeats(state);		
		// make a new thing?
		// ...only if absent?
		if (state.actionIs(ACTION_GETORNEW)) {
			JThing<T> thing = getThingFromDB(state);
			if (thing != null) {
				jthing = thing;
				return;
			}
			// absent => new
			state.setAction(ACTION_NEW);
		}
		// ...new
		if (state.actionIs(ACTION_NEW)) {
			// add is "special" as the only request that doesn't need an id
			String id = getId(state);
			jthing = doNew(state, id);
			jthing.setType(type);
		}
		
		// save?
		if (state.actionIs(ACTION_SAVE) || state.actionIs(ACTION_NEW)) {
			doSave(state);
			return;
		}
		// copy / save-as?
		if (state.actionIs("copy")) {
			doCopy(state);
			return;
		}
		if (state.actionIs("discard-edits") || state.actionIs("discardEdits")) {
			jthing = doDiscardEdits(state);
			return;
		}
		if (state.actionIs(ACTION_DELETE)) {
			jthing = doDelete(state);
			return;
		}
		// publish?
		if (state.actionIs(ACTION_PUBLISH)) {
			jthing = doPublish(state);
			if ( ! jthing.string().contains(KStatus.PUBLISHED.toString())) {
				Log.e(LOGTAG(), "doPublish json doesnt contain 'PUBLISHED' "+state);
			}
			return;
		}
		if (state.actionIs("unpublish")) {
			jthing = doUnPublish(state);
			return;
		}
		if (state.actionIs("archive")) {
			jthing = doArchive(state);
			return;
		}
	}


	protected void doAction2_blockRepeats(WebRequest state) {
		if (state.get(ALLOW_OVERLAPPING_EDITS)) {
			return;
		}
		String ckey = doAction2_blockRepeats2_actionId(state);
		if (ANTI_OVERLAPPING_EDITS_CACHE.getIfPresent(ckey)!=null) {
			Log.d(LOGTAG(), "Hit 2 second Anti overlap key: "+ckey);
			throw new WebEx.E409Conflict("Duplicate request within 2 seconds. Blocked for edit safety. "+state
					+" Note: this behaviour could be switched off via "+ALLOW_OVERLAPPING_EDITS);
		}		
		ANTI_OVERLAPPING_EDITS_CACHE.put(ckey, true);
	}

	/**
	 * @param state
	 * @return the id for this action -- this determines what counts as identical (and hence will be blocked)
	 */
	protected String doAction2_blockRepeats2_actionId(WebRequest state) {
		Map<String, Object> pmap = state.getParameterMap();
		String ckey = state.getAction()+FlexiGson.toJSON(pmap);
		return ckey;
	}


	/**
	 * Copy / save-as -- this is almost the same as save. 
	 * But it can clear some values which should not be copied -- e.g. external linking ids.
	 * @param state
	 */
	protected void doCopy(WebRequest state) {
		// clear linking ids
		T thing = getThing(state);
		if (thing instanceof IHasXId) {
			try {
				((IHasXId) thing).setAka(new ArrayList());
			} catch(UnsupportedOperationException ex) {
				// oh well
			}
		}
		// save 
		doSave(state);
	}



	/**
	 * Delete from draft and published!! (copy into trash)
	 * @param state
	 * @return null
	 */
	protected JThing<T> doDelete(WebRequest state) {
		String id = getId(state);
		// try to copy to trash
		try {
			JThing<T> thing = getThingFromDB(state);
			if (thing != null) {
				ESPath path = esRouter.getPath(dataspace, type, id, KStatus.TRASH);
				AppUtils.doSaveEdit2(path, thing, null, state, false);
			}
		} catch(Throwable ex) {
			Log.e(LOGTAG(), "copy to trash failed: "+state+" -> "+ex);
		}
		for(KStatus s : KStatus.main()) {
			if (s==KStatus.TRASH) continue;
			ESPath path = esRouter.getPath(dataspace,type, id, s);
			DeleteRequest del = es().prepareDelete(path.index(), path.type, path.id);
			del.setRefresh("wait_for");
			IESResponse ok = del.get().check();			
		}
		return null;
	}

	/**
	 * 
	 * @param state
	 * @return thing or null
	 * @throws TODO WebEx.E403
	 */
	protected JThing<T> getThingFromDB(WebRequest state) throws WebEx.E403 {
		ESPath path = getPath(state);
		if (path==null) {
			return null;
		}
		KStatus status = state.get(AppUtils.STATUS);
		// fetch from DB
		T obj = AppUtils.get(path, type);		
		if (obj!=null) {
			JThing thing = new JThing().setType(type).setJava(obj);
			return thing;
		}
		
		// Not found :(
		// was version=draft?
		if (status == KStatus.DRAFT) {			
			// Try for the published version
			// NB: all published should be in draft, so this should be redundant
			// ?? maybe refactor to use a getThinfFromDB2(state, status) method? But beware CharityServlet has overriden this
			WebRequest state2 = new WebRequest(state.request, state.response);
			state2.put(AppUtils.STATUS, KStatus.PUBLISHED);
			JThing<T> pubThing = getThingFromDB(state2);
			return pubThing;
		}
		// Was status unset? Maybe the published version got archived?
		if (status == null) {			
			// Try for an archived version
			WebRequest state2 = new WebRequest(state.request, state.response);
			state2.put(AppUtils.STATUS, KStatus.ARCHIVED);
			JThing<T> pubThing = getThingFromDB(state2);
			return pubThing;
		}		
		return null;
	}

	/**
	 * Use getId() to make an ESPath.
	 * NB: the path depends on status - defaulting to published 
	 * @param state
	 * @return null if there's no Id
	 */
	protected ESPath getPath(WebRequest state) {
		assert state != null;
		String id = getId(state);
		if (id==null) {
			return null;
		}
		if ("list".equals(id)) {
			throw new WebEx.E400(
					state.getRequestUrl(),
					"Bad input: 'list' was interpreted as an ID -- use /_list.json to retrieve a list.");
		}
		KStatus status = state.get(AppUtils.STATUS, KStatus.PUBLISHED);
		ESPath path = esRouter.getPath(dataspace, type, id, status);
		return path;
	}

	/**
	 * Make a new thing. The state will often contain json info for this.
	 * @param state
	 * @param id Can be null ??It may be best for the front-end to normally provide IDs. 
	 * @return
	 */
	protected JThing<T> doNew(WebRequest state, String id) {
		String json = getJson(state);
		T item;
		if (json == null) {
			try {
				item = doNew2_newBlankInstance(state, id);
			} catch (InstantiationException | IllegalAccessException e) {
				throw Utils.runtime(e);
			}
		} else {
			// from front end json
			item = Dep.get(Gson.class).fromJson(json, type);
			// TODO safety check ID! Otherwise someone could hack your object with a new object
//			idFromJson = AppUtils.getItemId(item);
		}
		// ID
		if (id != null) {
			if (item instanceof AThing) {
				((AThing) item).setId(id);
			}
			// else ??
		}
		if (item instanceof IInit) {
			((IInit) item).init();
		}
		return new JThing().setJava(item);
	}

	protected T doNew2_newBlankInstance(WebRequest state, String id) throws InstantiationException, IllegalAccessException {
		return type.newInstance();
	}




	private ESHttpClient _es;
	
	protected ESHttpClient es() {
		if (_es==null) {
			_es = Dep.get(ESHttpClient.class);
		}
		return _es;
	}
	
	protected final Class<T> type;
	protected JThing<T> jthing;

	protected final IESRouter esRouter;
	
	/**
	 * The focal thing's ID.
	 * This might be newly minted for a new thing
	 */
	protected String _id;
	
	/**
	 * Rarely used! Set the focal thing's ID.
	 */
	protected final void setId(String _id) {
		this._id = _id;
	}
	
	/**
	 * Optional support for dataspace based data access.
	 * Call {@link #setDataspace(CharSequence)} to use this
	 */
	// NB: the Dataspace class is not in the scope of this project, hence the super-class CharSequence
	protected CharSequence dataspace = null;
	
	public CrudServlet setDataspace(CharSequence dataspace) {
		this.dataspace = dataspace;
		return this;
	}
	
	/**
	 * suggested: date-desc
	 */
	protected String defaultSort;
	/**
	 * If true, run all items through {@link #augment(JThing, WebRequest)}
	 */
	protected boolean augmentFlag;

	/**
	 * If true, try to save edits into a git {project}-files repo (if it is present)
	 * 
	 * NB: Does NOT apply on local, to avoid messing with production files
	 */
	protected boolean gitAuditTrail;

	/**
	 * Usually unset. If set, _list text search only checks these fields.
	 */
	protected Collection<String> searchFields;
	
	/**
	 * Format is fieldname-asc/desc e.g. "amount-desc"
	 * 
	 * HACK "all" is handled as Sort._SHARD_DOC
	 */
	public static final SField SORT = new SField("sort");
	public static final String LIST_SLUG =  "_list";
	public static final IntField SIZE = new IntField("size");
	public static final IntField FROM = new IntField("from");
	public static final String ALL = "all";

	/**
	 * for using `next`
	 */
	private static final SField AFTER = new SField("after");

	/**
	 * Status: NOT used widely. But matches some use in the front end (see C.js)
	 */
	public static final String NEW_ID = "new";

	protected final JThing<T> doPublish(WebRequest state) throws Exception {
		// For publish, let's force the update.
		return doPublish(state, KRefresh.TRUE, false);
	}
	
	protected JThing<T> doPublish(WebRequest state, KRefresh forceRefresh, boolean deleteDraft) throws Exception {		
		String id = getId(state);
		Log.d("crud", "doPublish "+id+" by "+state.getUserId()+" "+state+" deleteDraft: "+deleteDraft);
		Utils.check4null(id); 
		getThingStateOrDB(state);
		return doPublish2(dataspace, jthing, forceRefresh, deleteDraft, id, state);
	}


	/**
	 * Idempotent -- sets the jthing field and reuses that. Get from state or DB
	 * @param state
	 * @return
	 */
	protected JThing<T> getThingStateOrDB(WebRequest state) {
		if (jthing!=null) {
			return jthing;
		}		
		// from request?
		getThing(state);
		// from DB?
		if (jthing==null) {
			setJthing(getThingFromDB(state));
		}
		return jthing;
	}

	/**
	 * @deprecated Not a normal thing to use
	 * @param jthing
	 */
	protected void setJthing(JThing<T> jthing) {
		this.jthing = jthing;
	}


	/**
	 * @param _jthing 
	 * @param forceRefresh
	 * @param deleteDraft
	 * @param id
	 * @return
	 */
	protected JThing<T> doPublish2(CharSequence dataspace, JThing<T> _jthing, 
			KRefresh forceRefresh, boolean deleteDraft, String id, WebRequest stateIgnored) 
	{		
		doBeforeSaveOrPublish(_jthing, stateIgnored);
		
		// id must match
		if (_jthing.java() instanceof AThing) {
			String thingId = ((AThing) _jthing.java()).getId();
			if (thingId==null || ACTION_NEW.equals(thingId)) {
				_jthing.put("id", id);
			} else if ( ! thingId.equals(id)) {
				// WTF?! NB: seen Jan 2021 with a badly setup internal call from one crudservlet to another. 
				throw new IllegalStateException(
						"ID mismatch "+_jthing.java().getClass().getSimpleName()+" java/json: "+thingId+" vs local: "+id);				
			}
		}		
		
		// ES paths
		ESPath draftPath = esRouter.getPath(dataspace, type, id, KStatus.DRAFT);
		ESPath publishPath = esRouter.getPath(dataspace, type, id, KStatus.PUBLISHED);
		ESPath archivedPath = esRouter.getPath(dataspace,type, id, KStatus.ARCHIVED);
		// do it
		JThing obj = AppUtils.doPublish(_jthing, draftPath, publishPath, archivedPath, forceRefresh, deleteDraft);
		return obj.setType(type);
	}
	
	/**
	 * Sets lastModified by default - override to add custom logic.
	 * This is called by both {@link #doSave(WebRequest)} and {@link #doPublish(WebRequest)}
	 * @param _jthing
	 * @param state
	 */
	protected void doBeforeSaveOrPublish(JThing<T> _jthing, WebRequest state) {
		if ( ! (_jthing.java() instanceof AThing)) {
			return;
		}
		// set last modified		
		AThing ting = (AThing) _jthing.java();
		ting.setLastModified(new Time());

		// Git audit trail?
		if (gitAuditTrail) {			
			doBeforeSaveOrPublish2_git(state, ting);
		}
	}


	protected File doBeforeSaveOrPublish2_git(WebRequest state, AThing ting) {
		if (BuildHacks.getServerType() != KServerType.PRODUCTION) {
			Log.d(LOGTAG(), "No git audit trail on "+BuildHacks.getServerType());
			return null;
		}
		KStatus status = KStatus.DRAFT;
		if (state!=null && state.actionIs(ACTION_PUBLISH)) status= KStatus.PUBLISHED; 
		File fd = getGitFile(ting, status);
		if (fd == null) {
			return null;
		}
		String json = prettyPrinter().toJson(ting);
		doSave2_file_and_git(state, json, fd);
		return fd;			
	}
	

	protected Gson prettyPrinter() {
		if (_prettyPrinter==null) {
			_prettyPrinter = AMain.main.init4_gsonBuilder().setPrettyPrinting().create();
		}
		return _prettyPrinter;
	}


	static Gson _prettyPrinter;
	


	protected JThing<T> doUnPublish(WebRequest state) {
		KStatus status = KStatus.DRAFT;
		return doUnPublish2(state, status);
	}
	
	private JThing<T> doUnPublish2(WebRequest state, KStatus status) {
		assert status!=null;
		String id = getId(state);
		Log.d("crud."+status, "doUnPublish "+id+" by "+state.getUserId()+" "+state);
		Utils.check4null(id); 
		getThingStateOrDB(state);

		ESPath draftPath = esRouter.getPath(dataspace,type, id, status);
		ESPath publishPath = esRouter.getPath(dataspace,type, id, KStatus.PUBLISHED);
		
		AppUtils.doUnPublish(jthing, draftPath, publishPath, status);
		
		state.addMessage(id+" has been moved to "+status.toString().toLowerCase());
		return jthing;
	}
	
	protected JThing<T> doArchive(WebRequest state) {
		return doUnPublish2(state, KStatus.ARCHIVED);
	}

	/**
	 * `new` gets turned into userid + nonce
	 * @param state
	 * @return 
	 */
	protected String getId(WebRequest state) {
		if (_id!=null) {
			return _id;
		}
		// Beware if ID can have a / in it!
//		String slug = state.getSlug();
		String[] slugBits = state.getSlugBits();
		// FIXME handle if the ID has a / encoded within it
		String sid = slugBits[slugBits.length - 1]; 
		// NB: slug-bit-0 is the servlet, slug-bit-1 might be the ID - or the dataspace for e.g. SegmentServlet
		if (slugBits.length == 1 || (dataspaceFromPath && slugBits.length == 2)) {
			if (state.actionIs("new")) {
				sid = "new"; // just the servlet => new
			} else {
				Log.w(LOGTAG(), "no ID "+state);
				return null;
			}
		}
		_id = getId2(state, sid);

		// dataspace the id??
		if (dataspace != null) {
			String dataspaceWart = "@"+dataspace;
			if ( ! _id.endsWith(dataspaceWart)) {
				_id = _id+dataspaceWart; 
			}
		}

		return _id;
	}

	protected String getId2(WebRequest state, String sid) {
		if (NEW_ID.equals(sid)) {
			String nicestart = StrUtils.toCanonical(
					Utils.or(state.getUserId(), state.get("name"), type.getSimpleName()).toString()
					).replace(' ', '_');
			sid = nicestart+"_"+Utils.getRandomString(8);
			// avoid ad, 'cos adblockers dont like it!
			if (sid.startsWith("ad")) {
				sid = sid.substring(2, sid.length());
			}
		}
		return sid;
	}


	protected void doStats(WebRequest state) {
		throw new WebEx.E404(state.getRequestUrl(), "_stats not available for "+type);
	}

	/**
	 * Note: status defaults to DRAFT! This is on the assumption you want a list for editing.
	 * ??should we switch to PUB_OR_DRAFT instead??
	 * 
	 * Returns JSend{hits: Item[], next: json-string, after, total, estimate:number}
	 * Warning: `total`=`estimate` and is an estimate!
	 * 
	 * @param state
	 * @return for debug purposes! The results are sent back in state
	 * @throws IOException
	 */
	public final List doList(WebRequest state) throws IOException {
		Time now = new Time();
		KStatus status = state.get(AppUtils.STATUS, KStatus.DRAFT);
		String q = state.get(CommonFields.Q);
		String prefix = state.get("prefix");
		String sort = state.get(SORT, defaultSort);
		int size = state.get(SIZE, 1000);
		int from = 0;
		try {
			from = state.get(FROM, 0);
		} catch(Exception ex) {
			// from also gets used for e.g. "from Alice"
			// so swallow exceptions 
			Log.d(LOGTAG(), ex+" "+state);
		}
		Period period = CommonFields.getPeriod(state);
		
		// for security filter on query (and later on results)
		YouAgainClient yac = Dep.get(YouAgainClient.class);
		List<AuthToken> tokens = yac.getAuthTokens(state);

		// Build the query!
		SearchResponse sr = doList2(q, prefix, status, sort, size,from, period, state, tokens);
		
		// Let's deal with ESHit and JThings
		List<ESHit<T>> _hits = sr.getHits(type);

		// init java objects (this acts as a safety check on bad data)
		_hits = init(_hits);
		
		// TODO dedupe can cause the total reported to be off
		List<ESHit<T>> hits2 = doList3_source_dedupe(status, _hits);
		
		// HACK: avoid created = during load just now
		for(ESHit<T> hit : hits2) {
			if ( ! hit.getJThing().isa(AThing.class)) continue;
			AThing at = (AThing) hit.getJThing().java();
			if (at.getCreated()!=null && at.getCreated().isAfter(now)) {
				at.setCreated(null);
			}
		}
		
		// security filter on results
		hits2 = doList2_securityFilter(hits2, state, tokens, yac);
		
		// sanitise for privacy
		int errCnt = 0;
		for(int i=0; i<hits2.size(); i++) {
			try {
				ESHit<T> h = hits2.get(i);
				JThing<T> cleansed = cleanse(h.getJThing(), state);
				if (cleansed==null) {
					continue;
				}
				ESHit ch = new ESHit(cleansed);
				hits2.set(i, ch);
			} catch (Throwable ex) {
				// Swallow and keep on trucking for the odd data error
				// Note: minor data privacy issue: this means the uncleansed data gets served up here!
				// Which will be useful for dbugging and should not happen in the wild.
				Log.e(LOGTAG()+".swallowed", ex);
				// Though if too much is breaking, then give up
				errCnt++;
				if (errCnt > 200) {
					Log.e(LOGTAG()+".ABORT", ex);
					break;
				}
			}
		}	
		
		// HACK: send back csv?
		if (state.getResponseType() == KResponseType.csv) {
			doSendCsv(state, hits2);
			return hits2;
		}
		
		// augment?
		if (augmentFlag) {
			for(int i=0; i<hits2.size(); i++) {
				ESHit<T> h = hits2.get(i);
				JThing<T> aug = augmentListItem(h.getJThing(), state);
				if (aug==null) continue;
				ESHit ah = new ESHit(aug);
				hits2.set(i, ah);
			}
		}
		// put together the json response	
		Long total = sr.getTotal();
		// ...adjust total
		if (_hits.size() < size) {
			total = (long) hits2.size(); // we have all of them
		} else if (total!=null) {
			// guess!
			double hitRatio = hits2.size() * 1.0/ size;
			double total2 = total*hitRatio;
			total = Math.round(total2);
		}
		
		List<Map> items = Containers.apply(hits2, h -> h.getJThing().map());
		List sa = sr.getSearchAfter(); // null if sort is not set
		String pit = sr.getPointInTime();
		String next = null;
		if (sa!=null) next = gson().toJson(new ArrayMap("searchAfter", sa, "pit", pit));
		String afterNext = state.get(AFTER);
//		String json = gson().toJson(
		Map jobj = new ArrayMap( // NB: see CrudSearchResults, which can consume this json
					"hits", items, 
					"total", total, // deprecated as unreliable
					"estimate", total,
					"next", next,
					"after", afterNext
				);
		JSend jsend = new JSend<>(jobj);
		jsend.send(state);
//		JsonResponse output = new JsonResponse(state).setCargoJson(json);
//		// ...send
//		WebUtils2.sendJson(output, state);
		return hits2;
	}
	
	/**
	 * Override to apply security filtering. 
	 * 
	 * NB: This is called by both /_list and by /id, so both share the same security check 
	 * @param hits2
	 * @param state 
	 * @param tokens 
	 * @param yac 
	 * @return
	 */
	protected List<ESHit<T>> doList2_securityFilter(List<ESHit<T>> hits2, WebRequest state, List<AuthToken> tokens, YouAgainClient yac) {
		return hits2;
	}
	
	/**
	 * Crude security filter: reject non-Good-Loop users with an exception
	 * @param state
	 * @throws WebEx.E401
	 */
	protected void securityHack_teamGoodLoop(WebRequest state) throws WebEx.E401 {
		boolean ok = isGLSecurityHack(state);
		if (ok) {
			return;
		}
		// No - sod off
		throw new WebEx.E401("This is for Team Good-Loop - Please ask for access");
	}


	/**
	 * HACK
	 * @param state
	 * @return true if they have a good-loop.com auth token
	 */
	public static boolean isGLSecurityHack(WebRequest state) {
		YouAgainClient yac = Dep.get(YouAgainClient.class);
		List<AuthToken> tokens = yac.getAuthTokens(state);
		for (AuthToken authToken : tokens) {
			if (authToken.getXId().isService("pseudo")) {
				continue; // healthy paranoia - these should fail below anyway
			}
			String name = authToken.getXId().getName();
			if ( ! WebUtils2.isValidEmail(name)) {
				// app2app also, but nothing else (eg Twitter)
				if (authToken.getXId().isService("app")) {
					if (name.endsWith("good-loop.com")) {
						return true;
					}
				}
				continue;
			}
			if (name.endsWith("@good-loop.com")) {
				if ( ! authToken.isVerified()) {
					// TODO verify
					Log.w(state.getRequestPath(), "not verified "+authToken);
				}
				// That will do for us for now
				return true;
			}			
		}
		return false;
	}


	/**
	 * Use this for "Team Good-Loop only" list security
	 * @param hits2
	 * @param state
	 * @param tokens
	 * @param yac
	 * @return
	 */
	protected List<ESHit<T>> doList2_securityFilter2_teamGoodLoop(List<ESHit<T>> hits2, WebRequest state,
				List<AuthToken> tokens, YouAgainClient yac) 
	{
		// HACK: are you a member of Team Good-Loop?
		securityHack_teamGoodLoop(state);
		return hits2;
	}
	
	


	/**
	 * Copy pasta from {@link GreenTagServlet}
	 * @param hits2
	 * @param state
	 * @param tokens
	 * @param yac
	 * @return
	 */
	protected List<ESHit<T>> doList2_securityFilter2_filterByShares(
			List<ESHit<T>> hits2, WebRequest state, List<AuthToken> tokens, YouAgainClient yac			
	) {
		// HACK public access?
		if (isPublicAccess(state)) {
			Log.d(LOGTAG(), "public access - unfiltered list "+hits2.size());
			return hits2;
		}
		// TODO should we get the shares first, then filter in the ES query?		
		// TODO refactor into CrudServlet -- but oxid and campaign are GreenTag specific		
		boolean isGL = doList3_securityFilter3_filterByShares2_isGLSecurityHack(state); // Let tech support see everything
		if (isGL) {
			return hits2;
		}		
		// HACK allow Impact Hub to load the Advertiser (but filter ListLoad _list calls for Green Tag Generator)
		if ( ! isListRequest(state.getSlug())) {
			return hits2;
		}
		// nothing to check?
		if (hits2.isEmpty()) {
			return hits2;
		}
				
//		// HACK allow a switch off!!
//		if (Utils.yes(state.get("unfiltered"))) {
//			return hits2;
//		}
		
		// shares
		T pojo = hits2.get(0).getJThing().java();
		XId uxid = state.getUserId();
		// ...Collect the things shared with the authorised user
		Map<Class,List<String>> sharedType = new ArrayMap();
		String app = yac.getIssuer();
		// This was not getting the same shares as the front-end request!!
		// Different JWT tokens? But why? The front-end should send its JWTs with the request
		// Ah: the front-end and shares use app=portal.good-loop.com
		// But this is using app=good-loop :(
		// HACK just override for a moment
		if ("portal".equals(AMain.appName)) {
			app = "portal.good-loop.com";
		}
		// include direct shares of this type
		List<String> directshares = yac.sharing().getSharedWithItemIds(yac.getIssuer(), app, tokens, type.getSimpleName());
		// shared-by e.g. Agency via agencyId?
		Map<Class,String> shareBy_field4type;
		if (pojo instanceof ISecurityByShares) {
			ISecurityByShares eg = (ISecurityByShares) pojo;
			shareBy_field4type = eg.getShareBy();		
			for(Class k : shareBy_field4type.keySet()) {						
				List<String> sharedCampaigns = yac.sharing().getSharedWithItemIds(yac.getIssuer(), app, tokens, k.getSimpleName());
				sharedType.put(k, sharedCampaigns);
			}
		} else {
			shareBy_field4type = Collections.EMPTY_MAP;
		}
		// filter by shares 
		List<ESHit<T>> myHits = Containers.filter(hits2, hit -> {
			T gtag = hit.getJThing().java();
			// direct share?
			if (gtag instanceof AThing) {
				String id = ((AThing) gtag).getId();
				if (directshares.contains(id)) {
					return true;
				}
			}
			// Yours?
			XId oxid = ((AThing)gtag).oxid;
			if (uxid != null && uxid.equals(oxid)) {
				return true;
			}
			for(Class k : shareBy_field4type.keySet()) {
				// Get the relevant ID - e.g. if k=Advertiser, then gtagValue = gtag.vertiser
				Object gtagValue = ReflectionUtils.getPrivateField(gtag, shareBy_field4type.get(k));
				List<String> shared = sharedType.get(k);
				if (gtagValue != null && shared.contains(gtagValue)) {
					return true;
				}
			}	
			return false;
		});
		
		return myHits;
	}


	/**
	 * Allow overrides eg for tighter security in some areas
	 * @param state
	 * @return
	 */
	protected boolean doList3_securityFilter3_filterByShares2_isGLSecurityHack(WebRequest state) {
		return isGLSecurityHack(state);
	}

	/**
	 * This is NOT called by default, but sub-classes can choose to use it.
	 * 
	 * Save text to file, and git (add)+commit+push.
	 * E.g.
	 * 
<code><pre>
	File fd = getGitFile(ad, KStatus.PUBLISHED);
	if (fd != null) {
		String json = prettyPrinter.toJson(ad);
		doSave2_file_and_git(state, json, fd);
	}
	return _ad;
</pre></code>
	 * 
	 * @param state
	 * @param text
	 * @param fd
	 */
	protected void doSave2_file_and_git(WebRequest state, String text, File fd) {
		try {						
			if (text==null) return; // paranoia
			String old = fd.isFile()? FileUtils.read(fd) : "";
			if (text.equals(old)) {
				return;
			}
			Log.d(LOGTAG(), "doSave2_file_and_git "+fd);
			FileUtils.write(fd, text);
//			Git pull, commit and push!
			try {
				GitTask gt0 = new GitTask(GitTask.PULL, fd);
				gt0.run();
				gt0.close();
				Log.d(LOGTAG(), gt0.getOutput());
			} catch(Exception ex) {
				// stach local edits which may be causing a problem
				GitTask gt0a = new GitTask(GitTask.STASH, fd);
				gt0a.addArg("--include-untracked");
				gt0a.run();
				gt0a.close();
				Log.d(LOGTAG(), gt0a.getOutput());
				// try to pull again
				GitTask gt0 = new GitTask(GitTask.PULL, fd);
				gt0.run();
				gt0.close();
				Log.d(LOGTAG(), gt0.getOutput());
			}
			
			GitTask gt1 = new GitTask(GitTask.ADD, fd);
			gt1.run();
			Log.d(LOGTAG(), gt1.getOutput());
			FileUtils.close(gt1);
			
			GitTask gt2 = new GitTask(GitTask.COMMIT, fd);
			String uname = state.getUserId()==null? "anon" : state.getUserId().name;
			gt2.setMessage(uname);
			gt2.run();
			Log.d(LOGTAG(), gt2.getOutput());
			FileUtils.close(gt2);
			
			GitTask gt3 = new GitTask(GitTask.PUSH, fd);
			gt3.run();			
			Log.d(LOGTAG(), gt3.getOutput());
			FileUtils.close(gt3);
			Log.d(LOGTAG(), "...doSave2_file_and_git "+fd+" done");
		} catch(Throwable ex) {
			Log.w(LOGTAG(), "Error while saving to Git: "+ex+" "+ 
					"Your save worked, but the audit trail did not update. Ask sysadmin@good-loop.com to do `cd "
							+(fd==null? "null?!" : fd.getParentFile())+"; git pull`");
		}
	}

	
	/**
	 * Run results through deserialisation to catch any bugs.
	 * Bugs are logged, but they do _not_ disrupt returning the rest of the list.
	 * This is so one bad data item can't block an API service.
	 * 
	 * @param _hits
	 * @return hits (filtered for no-exceptions)
	 */
	private List<ESHit<T>> init(List<ESHit<T>> _hits) {
		List<ESHit<T>> hits = new ArrayList(_hits.size());
		for (ESHit<T> h : _hits) {
			try {
				T pojo = h.getJThing().java();
				if (pojo instanceof IInit) {
					((IInit) pojo).init();
				}
				hits.add(h);
			} catch(Throwable ex) {
				// log, swallow, and carry on
				Log.e("crud", "cause: "+h+" "+ex); // less info actually -- we can look it up from the server if we need it +" source: "+h.getSource()+" "+Printer.toString(ex, true));
			}
		}
		return hits;		
	}


	/**
	 * Called on outgoing json to add extra info IF augmentFlag is set. Override to do anything. 
	 * @param jThing Never null. Modify this if you want
	 * @param state
	 * @return modified JThing or null
	 * @see #augmentFlag
	 */
	protected JThing<T> augment(JThing<T> jThing, WebRequest state) {
		// no-op by default
		return null;
	}

	/**
	 * Called on outgoing json to add extra info IF augmentFlag is set. Override to do anything. 
	 * @param jThing Never null. Modify this if you want
	 * @param state
	 * @return modified JThing or null (null => no-change)
	 * @see #augmentFlag
	 */
	protected JThing<T> augmentListItem(JThing<T> jThing, WebRequest state) {
		// no-op by default
		return null;
	}

	/**
	 * Do the search! 
	 * 
	 * Does NOT dedupe (eg multiple copies with diff status) or security cleanse.
	 * @param prefix 
	 * @param from TODO
	 * @param tokens 
	 * @param num 
	 */
	public final SearchResponse doList2(String q, String prefix, KStatus status, String sort, 
			int size, int from, Period period, 
			WebRequest stateOrNull, List<AuthToken> tokens) 
	{
		// copied from SoGive SearchServlet
		SearchRequest s = new SearchRequest(es());
		/// which index? draft (which should include copies of published) by default
		doList3_setIndex(status, s);
		
		// query
		ESQueryBuilder qb = doList3_ESquery(q, prefix, period, stateOrNull);
		
		// security?
		qb = doList3_securityFilterOnQuery(qb, stateOrNull, tokens);
		
		if (qb!=null) {
			s.setQuery(qb);
		}

		// paging?
		String afterNext = stateOrNull==null?null : stateOrNull.get(AFTER);
		String pit = null;
		if (afterNext!=null) {
			Map an = gson().fromJson(afterNext);
			List sa = SimpleJson.getList(an,"searchAfter");
			pit = (String) an.get("pit"); // ??old??
			if (pit!=null) {
				s.setPointInTime(pit, keep_alive);
			}
			s.setSearchAfter(sa);
		}

		// Sort e.g. sort=date-desc for most recent first
		if (sort!=null) {			
			doList3_addSort(sort, s, pit);
		}
		
		s.setSize(size);
		s.setFrom(from); // allows for paging within 10k of results
		s.setDebug(true);
//		s.setScroll(null) TODO support for big +10k data

		// Call the DB
		SearchResponse sr = s.get();
		
		
		return sr;
	}

	static Dt keep_alive = new Dt(5, TUnit.MINUTE);

	/**
	 * see {@link #SORT}
	 * @param sort
	 * @param s
	 * @param pit2 
	 */
	private void doList3_addSort(String sort, SearchRequest s, String pit) {
		// HACK: all => sort so we can page over all results
		if ("all".equals(sort)) {
			s.addSort(Sort._SHARD_DOC);
			// also needs a point-in-time
			if (pit==null) {
				Collection<String> indices = s.getIndices();
				pit = _es.getPointInTime(indices.toArray(StrUtils.ARRAY), keep_alive);
				s.setPointInTime(pit, keep_alive);
			}
			return;
		}
		// split on comma to support hierarchical sorting, e.g. priority then date
		String[] sorts = sort.split(",");
		for (String sortBit : sorts) {
			// split into field and up/down order
			KSortOrder order = KSortOrder.asc;
			if (sortBit.endsWith("-desc")) {
				sortBit = sortBit.substring(0, sortBit.length()-5);
				order = KSortOrder.desc;
			} else if (sortBit.endsWith("-asc")) {
				sortBit = sortBit.substring(0, sortBit.length()-4);
			}				
			Sort _sort = doList4_addSort(sortBit, order);
			s.addSort(_sort);
		}
	}


	/**
	 * Override to add extra security clauses to the query. Does nothing by default.
	 * @param qb
	 * @param stateOrNull
	 * @param tokens
	 * @return
	 */
	protected ESQueryBuilder doList3_securityFilterOnQuery(ESQueryBuilder qb, WebRequest stateOrNull,
			List<AuthToken> tokens) 
	{
		return qb;
	}


	/**
	 * Add a sort. You can override this to specify e.g. {"missing" : "_first"}
	 * See https://www.elastic.co/guide/en/elasticsearch/reference/7.9/sort-search-results.html
	 * @param sortBit
	 * @param order
	 * @return 
	 */
	protected Sort doList4_addSort(String sortBit, KSortOrder order) {
		Sort _sort = new Sort().setField(sortBit).setOrder(order);			
		return _sort;
	}


	protected void doList3_setIndex(KStatus status, SearchRequest s) {
		switch(status) {
		case ALL_BAR_TRASH:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index(),
					esRouter.getPath(dataspace, type, null, KStatus.DRAFT).index(),
					esRouter.getPath(dataspace, type, null, KStatus.ARCHIVED).index()
				);
			break;
		case PUB_OR_ARC:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index(),
					esRouter.getPath(dataspace, type, null, KStatus.ARCHIVED).index()
				);
			break;
		case PUB_OR_DRAFT:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index(),
					esRouter.getPath(dataspace, type, null, KStatus.DRAFT).index()
				);
			break;
		case DRAFT_OR_PUB:
			s.setIndices(
					esRouter.getPath(dataspace, type, null, KStatus.DRAFT).index(),
					esRouter.getPath(dataspace, type, null, KStatus.PUBLISHED).index()
				);
			break;
		default:
			// normal
			s.setIndex(esRouter.getPath(dataspace, type, null, status).index());
		}
	}


	/**
	 * 
	 * @param q
	 * @param prefix
	 * @param period
	 * @param stateOrNull
	 * @return can this be null?? best to guard against nulls
	 * 
	 * <p>
	 * See AppUtils#makeESFilterFromSearchQuery(SearchQuery, Time, Time) which does the heavy lifting
	 */
	protected ESQueryBuilder doList3_ESquery(String q, String prefix, Period period, WebRequest stateOrNull) {
		ESQueryBuilder qb = null;
		
		q = doList4_ESquery_customString(q);
		// HACK no key:value in a prefix query
		if (prefix!=null && prefix.indexOf(':') != -1) {
			if (q==null) q = prefix;
			else q = "("+q+") AND "+prefix;
			prefix = null;
		}
		if (prefix != null) {
			BoolQueryBuilder esPrefix = doList4_ESquery_prefix(prefix);
			assert qb==null;
			qb = esPrefix;
		} //./prefix
		
		if ( q != null) {
			// convert "me" to specific IDs	FIXME but what about e.g. "me and my girl"?
			if (Pattern.compile(":me\\b").matcher(q).find()) {
				if (stateOrNull==null) {
					throw new NullPointerException("`me` requires webstate to resolve who: "+q);
				}
				YouAgainClient ya = Dep.get(YouAgainClient.class);
				List<AuthToken> tokens = ya.getAuthTokens(stateOrNull);
				StringBuilder mes = new StringBuilder();
				for (AuthToken authToken : tokens) {
					mes.append(authToken.xid+" OR ");
				}
				if (mes.length()==0) {
					Log.w("crud", "No mes "+q+" "+stateOrNull);
					mes.append("ANON OR " ); // fail - WTF? How come no logins?!
				}
				StrUtils.pop(mes, 4);
				q = q.replaceAll(":me\\b", ":"+mes.toString());
			}
			// TODO match on all?
//			// HACK strip out unset ?? Why was this commented out?? Is unset handled OK?? 
//			if (q.contains(":unset")) {
//				Matcher m = Pattern.compile("(\\w+):unset").matcher(q);
//				m.find();
//				String prop = m.group(1);
//				String q2 = m.replaceAll("").trim();
//				q = q2;
//				ESQueryBuilder setFilter = ESQueryBuilders.existsQuery(prop);
//				qb = ESQueryBuilders.boolQuery().mustNot(setFilter);
//			}	
			// Add the Query!
			if ( ! Utils.isBlank(q) && ! ALL.equalsIgnoreCase(q)) { // ??make all case-sensitive??
				SearchQuery sq = new SearchQuery(q);
				BoolQueryBuilder esq = AppUtils.makeESFilterFromSearchQuery(sq, null, false, null, false, searchFields);			
				qb = ESQueryBuilders.must(qb, esq);
			}
		} // ./q		
		
		if (period != null) {
			// option for modified or another date field?
			String timeField = stateOrNull==null? null : stateOrNull.get("period");
			if (timeField==null) timeField = "created";
			ESQueryBuilder qperiod = ESQueryBuilders.dateRangeQuery(timeField, period.first, period.second);
			qb = ESQueryBuilders.must(qb, qperiod);
		}
		
		// NB: exq can be null for ALL
		ESQueryBuilder exq = doList4_ESquery_custom(stateOrNull);
		qb = ESQueryBuilders.must(qb, exq);
		return qb;
	}

	private BoolQueryBuilder doList4_ESquery_prefix(String prefix) {
		assert prefix != null;
		assert ! prefix.contains(":");
		prefix = prefix.trim(); // bugfix Feb 9th 2022

		BoolQueryBuilder orESQ = ESQueryBuilders.boolQuery();
		orESQ.minimumNumberShouldMatch(1);
		// straight search -- allows for match on ID
		BoolQueryBuilder esSearch = AppUtils.makeESFilterFromSearchQuery(new SearchQuery(prefix), null, null);
		orESQ.should(esSearch);
		
		// Hack: convert punctuation into spaces, as ES would otherwise say query:"P&G" !~ name:"P&G"
		String cprefix = StrUtils.toCanonical(prefix);
		// But also try without that!
		// NB: avoid a duplicate query
		String[] ps = prefix.equals(cprefix)? new String[]{prefix} : new String[]{prefix, cprefix};
		for(String _prefix : ps){				
			BoolQueryBuilder prefixESQ = ESQueryBuilders.boolQuery();
			// Hack: Prefix should be one word. If 2 are sent -- turn it into a query + prefix
			int spi = _prefix.lastIndexOf(' ');
			if (spi != -1) {				
				assert _prefix.equals(_prefix.trim()) : "untrimmed?! "+_prefix;
				String qbit = _prefix.substring(0, spi);
				BoolQueryBuilder esBitSearch = AppUtils.makeESFilterFromSearchQuery(new SearchQuery(qbit), null, null);
				prefixESQ.filter(esBitSearch); // this should be an AND really
				_prefix = _prefix.substring(spi+1);
			}
			// prefix is on a field(s) -- we use name by default			
			for(String field : prefixFields) {
				prefixESQ.should(ESQueryBuilders.prefixQuery(field, _prefix));
			}		
			orESQ.should(prefixESQ);
		}
		
		// either by search, or prefix, or canonical prefix			
		orESQ.minimumNumberShouldMatch(1);
		return orESQ;
	}


/**
 * 		// If user requests ALL_BAR_TRASH, they want to see draft versions of items which have been edited
		// So when de-duping, give priority to entries from .draft indices where the object is status: DRAFT

 * @param requestStatus
 * @param hits
 * @return unique hits, source
 */
	private List<ESHit<T>> doList3_source_dedupe(KStatus requestStatus, List<ESHit<T>> hits) {
		if ( ! KStatus.isMultiIndex(requestStatus)) {
			// One index = no deduping necessary.
//			ArrayList<Object> hits2 = Containers.apply(hits, h -> h.get("_source"));
			return hits;
		}
		List<ESHit<T>> hits2 = new ArrayList<>();
		// de-dupe
//		KStatus preferredStatus = status==KStatus.ALL_BAR_TRASH? KStatus.DRAFT : KStatus.PUB_OR_ARC;
		List<Object> idOrder = new ArrayList<Object>(); // original ordering
		Map<Object, ESHit<T>> things = new HashMap<>(); // to hold "expected" version of each hit
		
		for (ESHit<T> h : hits) {
			// pull out the actual object from the hit (NB: may be Map or AThing)
//			Object hit = h.getSource();
			Object id = getIdFromHit(h);			
			// First time we've seen this object? Save it.
			if ( ! things.containsKey(id)) {
				idOrder.add(id);
				things.put(id, h);
				continue;
			}
			// Which copy to keep?
			// Is this an object from .draft with non-published status? Overwrite the previous entry.
			Object index = h.getIndex();
			String shitStatus = getStatus(h.getJThing());
			if (shitStatus==null) { // odd!
				Log.w(LOGTAG(), "null status for "+id+" "+h.getJThing());
			}
			KStatus hitStatus = shitStatus==null? null : KStatus.valueOf(shitStatus);			
			if (requestStatus == KStatus.ALL_BAR_TRASH || requestStatus==KStatus.DRAFT_OR_PUB) {
				// prefer draft
				if (index != null && index.toString().contains(".draft")) {
					things.put(id, h);	
				}
			} else {
				// prefer published over archived
				if (KStatus.PUBLISHED == hitStatus) {
					things.put(id, h);	
				}										
			}
		}
		// Put the deduped hits in the list in their original order.
		for (Object id : idOrder) {
			if (things.containsKey(id)) {
				hits2.add(things.get(id));
			}
		}
		return hits2;
	}


	/**
	 * convenience
	 * @return
	 */
	protected Gson gson() {
		return Dep.get(Gson.class);
	}



	/**
	 * Remove sensitive details (like password or budget) for privacy - override to do anything!
	 * 
	 * 
	 * @param thing Best NOT to modify this directly. Make a copy.
	 * @param state
	 * @return null if fine, or modified JThing if edits are wanted
	 */
	protected JThing<T> cleanse(JThing<T> thing, WebRequest state) {
		return null;
	}


	private String getStatus(JThing h) {
		Object s;
		if (h.java() instanceof AThing) {
			s = ((AThing) h.java()).getStatus();
		} else {
			s = h.map().get("status");
		}
		if (s==null) return null;
		String ss = String.valueOf(s);
		return ss;
	}



	/**
	 * 
	 * @param hit Map from ES, or AThing
	 * @return
	 */
	private Object getIdFromHit(ESHit<T> hit) {
		Object id = hit.getJThing().map().get("id");
		return id;
	}



	protected void doSendCsv(WebRequest state, List<ESHit<T>> hits2) {
		StringWriter sout = new StringWriter();
		CSVWriter w = new CSVWriter(sout, new CSVSpec());
		
		Json2Csv j2c = new Json2Csv(w);		
		List<String> headers = state.get(new ListField<String>("headers"));
		if (headers==null) headers = Arrays.asList("id", "name", "created", "status");
		j2c.setHeaders(headers);
		// TODO!
//		j2c.run(hits2);		
		
		// send
		String csv = sout.toString();
		state.getResponse().setContentType(WebUtils.MIME_TYPE_CSV); // + utf8??
		WebUtils2.sendText(csv, state.getResponse());
	}
	

	/**
	 * Override to modify query string for corner cases.
	 * @param q
	 * @return modified query string
	 */
	protected String doList4_ESquery_customString(String q) {
		return q;
	};


	/**
	 * Override to add custom filtering.
	 * @param state
	 * @return null or a query. This is ANDed to the normal query.
	 */
	protected ESQueryBuilder doList4_ESquery_custom(WebRequest state) {
		return null;
	}


	/**
	 * 
	 * NB: doPublish does NOT save first!
	 * 
	 * NB: Uses AppUtils#doSaveEdit2(ESPath, JThing, WebRequest, boolean) to do a *merge* into ES.
	 * So this will not remove parts of a document (unless you provide an over-write value).
	 * 
	 * Why use merge?
	 * This allows for partial editors (e.g. edit the budget of an advert), and reduces the collision
	 * issues with multiple online editors.
	 * 
	 * 
	 * @param state
	 */
	protected void doSave(WebRequest state) {		
		XId user = state.getUserId(); // TODO save who did the edit + audit trail
		
		// If there is a diff, apply it
		// We *also* save the diff as an update script
		String diff = state.get("diff");		
		boolean savedByDiff = false;
		if (diff != null) {
			Object jdiff = WebUtils2.parseJSON(diff);
			diffs = Containers.asList(jdiff);
			JThing<T> oldThing = getThingFromDB(state);
			if (oldThing==null || oldThing.map()==null) {
				Log.w(LOGTAG(), "(handled as a non-diff save) cant applyDiff to null old object "+state);
			} else {
				applyDiff(oldThing);
				savedByDiff = true;
				jthing = oldThing; // NB: getThing(state) below will now return the diff-modified oldThing
			}
		}
		if ( ! savedByDiff) {
			T thing = getThing(state); 
			assert thing != null : "null thing?! "+state;
		}
		
		// This has probably been done already in getThing(), but harmless to repeat
		// run the object through Java, to trigger IInit
		T pojo = jthing.java();
		
		doBeforeSaveOrPublish(jthing, state);
		
		// add security?
		doSave2_setSecurity(state, pojo);
		
		{	// update
			String id = getId(state);
			assert id != null : "No id? cant save! "+state; 
			ESPath path = esRouter.getPath(dataspace,type, id, KStatus.DRAFT);
			AppUtils.doSaveEdit(path, jthing, diffs, state);
			Log.d("crud", "doSave "+path+" by "+state.getUserId()+" "+state+" "+jthing.string());
		}
	}
	

	/**
	 * HACK: can be a list of Map or JsonPatchOp
	 */
	List diffs;
	
	/**
	 * This can modify diffs!
	 * @param room
	 * @param diffs Each diff is {op:replace, path:/foo/bar, value:v}
	 * @return
	 */
	final void applyDiff(JThing<T> room) {		
		if (diffs.isEmpty()) {
			return;
		}
		JsonPatch jp = JsonPatch.fromJson(diffs);
		assert room != null;		
		Map<String, Object> thingMap = room.map();
		thingMap = thingMap==null? new HashMap() : new HashMap(thingMap); // a new blank, or a defensive copy
		jp.apply(thingMap);
		room.setMap(thingMap);
		// modify diffs?
		if ( ! jp.getModifiedDiff4diff().isEmpty()) {
			// swap diffs
			ArrayList<JsonPatchOp> modDiffs = new ArrayList();
			for(JsonPatchOp d : jp.getDiffs()) {
				JsonPatchOp d2 = jp.getModifiedDiff4diff().get(d);
				modDiffs.add(d2==null? d : d2);
			}
			Log.d(LOGTAG(), "modDiffs: "+modDiffs+" originalDiffs: "+diffs);
			diffs = modDiffs;
		}
	}

	/**
	 * Override to implement!
	 * @param state
	 * @param pojo
	 */
	protected void doSave2_setSecurity(WebRequest state, T pojo) {
		// TODO Auto-generated method stub		
	}



	/**
	 * Get from field or state. Does NOT call the database.
	 * @param state
	 * @return
	 * @see #getThingFromDB(WebRequest)
	 */
	protected T getThing(WebRequest state) {
		if (jthing!=null) {
			return jthing.java();
		}
		String json = getJson(state);
		if (json==null) {
			return null;
		}
		setJthing(new JThing(json).setType(type));
		return jthing.java();
	}

	protected String getJson(WebRequest state) {
		String json = state.get(new SField(AppUtils.ITEM.getName()));
		if (json!=null) return json;
		// body?
		json = state.getPostBody();
		if (json!=null) {
			if (json.startsWith("{") || json.startsWith("[")) {
				return json;
			}
		}
		return null;
	}
	
}

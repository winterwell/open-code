//package com.goodloop.gsheets;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.security.GeneralSecurityException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import com.google.api.client.auth.oauth2.Credential;
//import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
//import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
//import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
//import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
//import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
//import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
//import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.JsonFactory;
//import com.google.api.client.json.jackson2.JacksonFactory;
//import com.google.api.client.util.store.FileDataStoreFactory;
//import com.google.api.services.sheets.v4.Sheets;
//import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values.Update;
//import com.google.api.services.sheets.v4.SheetsScopes;
//import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
//import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
//import com.google.api.services.sheets.v4.model.CellData;
//import com.google.api.services.sheets.v4.model.CellFormat;
//import com.google.api.services.sheets.v4.model.Color;
//import com.google.api.services.sheets.v4.model.ExtendedValue;
//import com.google.api.services.sheets.v4.model.GridRange;
//import com.google.api.services.sheets.v4.model.RepeatCellRequest;
//import com.google.api.services.sheets.v4.model.Request;
//import com.google.api.services.sheets.v4.model.Sheet;
//import com.google.api.services.sheets.v4.model.SheetProperties;
//import com.google.api.services.sheets.v4.model.Spreadsheet;
//import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
//import com.google.api.services.sheets.v4.model.TextFormat;
//import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
//import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
//import com.google.api.services.sheets.v4.model.ValueRange;
//import com.winterwell.utils.FailureException;
//import com.winterwell.utils.Printer;
//import com.winterwell.utils.Utils;
//import com.winterwell.utils.containers.Containers;
//import com.winterwell.utils.containers.IntRange;
//import com.winterwell.utils.gui.GuiUtils;
//import com.winterwell.utils.io.FileUtils;
//import com.winterwell.utils.log.Log;
//import com.winterwell.utils.web.WebUtils2;
//import com.winterwell.web.app.Logins;
//
//
///**
// * Google's docs - see https://developers.google.com/sheets/api
// 
// How to Authorise
// This class will look for app oauth credentials/config in:
// logins moneyscript.good-loop.com credentials.json
// It will then call google to get a user Credential
// which is stored in logins/tokens
// 
// TODO user specific auth
// TODO support browser<>server auth (current code only works on local - but you can then share the token to a server)
// 
// * @testedby GMailClientTest
// * 
// * @author Google, daniel
// *
// */
//public class GMailClient {
//	
//	private static final String APP = "moneyscript.good-loop.com";
//	private static final String APPLICATION_NAME = "MoneyScript by Good-Loop";
//	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
//	/**
//	 * in logins dir
//	 */
//	private static final String TOKENS_DIRECTORY_PATH = "tokens/gmail";
//	private static final String LOGTAG = "GMailClient";
//	private String accessType = "offline";
//	
//	/**
//	 * 
//	 * @param accessType online|offline
//	 */
//	public void setAccessType(String accessType) {
//		this.accessType = accessType;
//	}
//	
//	/**
//	 * Global instance of the scopes required by this quickstart. If modifying these
//	 * scopes, delete your previously saved tokens/ folder.
//	 */
//	private static final List<String> SCOPES = Collections.singletonList(
//		 SheetsScopes.SPREADSHEETS
//			);
//	/**
//	 * hack to avoid upsetting gsheets Nov 2021
//	 */
//	private static final int MAX_ROW = 10000;
//	private static final int MAX_COL = 10000;
//	private static final int MAX_ROWS_PER_UPDATE = 1000;
//
//	String userId;
//	private String redirectUri;
//	public void setRedirectUri(String redirectUri) {
//		this.redirectUri = redirectUri;
//	}
//	public void setUserId(String userId) {
//		this.userId = userId;
//	}
//	
//	/**
//	 * Creates an authorized Credential object.
//	 * 
//	 * @param HTTP_TRANSPORT The network HTTP Transport.
//	 * @return An authorized Credential object.
//	 * @throws IOException If the credentials.json file cannot be found.
//	 */
//	private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
//		Log.i(LOGTAG, "get credentials...");
//		// Load client secrets.
//		File credsFile = Logins.getLoginFile(APP, "credentials.json");
//		if (credsFile == null) {
//			throw new FileNotFoundException(Logins.getLoginsDir()+"/"+APP+"/credentials.json");
//		}
//		File tokensDir = new File(Logins.getLoginsDir(), TOKENS_DIRECTORY_PATH);
//		Log.d(LOGTAG, "...read credentials.json "+credsFile);
//		InputStream in = new FileInputStream(credsFile);
//		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));		
//		String debugcs = FileUtils.read(credsFile);
//		
//		// Build flow and trigger user authorization request.
////		?? scopes = scopes.add("https://www.googleapis.com/auth/userinfo.email"); //??
//		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
//				clientSecrets, SCOPES)
//						.setDataStoreFactory(new FileDataStoreFactory(tokensDir))
//						.setAccessType(accessType)						
//						.build();
//				
//		if (userId!=null) {
//			Credential lc = flow.loadCredential(userId);
//			if (lc != null) {
//				return lc;
//			}
////			The first step is to call loadCredential(String) based on the known user ID to check if the end-user's 
////			credentials are already known. If not, call newAuthorizationUrl() and direct the end-user's browser to an 
////			authorization page. The web browser will then redirect to the redirect URL with a "code" query parameter 
////			which can then be used to request an access token using newTokenRequest(String). 
////			Finally, use createAndStoreCredential(TokenResponse, String) to store and obtain a credential for 
////			accessing protected resources.
//			GoogleAuthorizationCodeRequestUrl nau = flow.newAuthorizationUrl();
//			List<String> redirectUris = clientSecrets.getWeb().getRedirectUris();
//			if ( ! redirectUris.contains(redirectUri)) {
//				Log.w(LOGTAG, "Error likely - redirect unknown: "+redirectUri+" vs "+redirectUris);
//			}
//			nau.setRedirectUri(redirectUri);
//			String url = nau.build();
//			WebUtils2.browseOnDesktop(url);
//			String authCode = GuiUtils.askUser("Auth code?");
//			if (authCode != null) {
//				GoogleAuthorizationCodeTokenRequest treq = flow.newTokenRequest(authCode);
//				GoogleTokenResponse tr = treq.execute();
//				Credential cred = flow.createAndStoreCredential(tr, userId);
//				return cred;
//			}
//		}
//		
//		int receiverPort = 7149;
//		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(receiverPort).build();
//		Log.d(LOGTAG, "...get credentials AuthorizationCodeInstalledApp.authorize() with port "+receiverPort+"...");
//		VerificationCodeReceiver _receiver = receiver;
//		AuthorizationCodeInstalledApp acia = new AuthorizationCodeInstalledApp(flow, _receiver);
//		Credential cred = acia.authorize("user");
//		Log.i(LOGTAG, "...get credentials done. Expiry seconds: "+cred.getExpiresInSeconds());
//		return cred;
//	}
//
//	/**
//	 * Handles login and auth. 
//	 * The first time you run this, it will ask you to authorise access via a browser window. 
//	 * Afterwards, it will use a stored token.
//	 * 
//	 * Warning: auth setup only works on local! For servers, we have to send the token (via the logins repo).
//	 * 
//	 * @return
//	 */
//	private Sheets getService() {
//		Log.i(LOGTAG, "getService...");
//		try {
//			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
//			Credential cred = getCredentials(HTTP_TRANSPORT);
//			assert cred != null;
//			Sheets.Builder sb = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred);
//			assert sb != null : "getService No Builder?!";
//			sb = sb.setApplicationName(APPLICATION_NAME);
//			assert sb != null : "getService set name => No Builder?!";
//			Sheets service = sb.build();
//			Log.i(LOGTAG, "getService done: "+service);	
//			return service;
//		} catch(Throwable ex) {
//			Log.i(LOGTAG, "getService :( "+Printer.toString(ex, true)); // make sure its logged
//			throw Utils.runtime(ex);
//		}
//	}
//
//	/**
//	 * See https://developers.google.com/sheets/api/guides/batchupdate#java
//	 * https://developers.google.com/sheets/api/samples/writing
//	 * @throws IOException
//	 * @throws GeneralSecurityException
//	 */
//	public Object updateValues(String spreadsheetId, List<List<Object>> values) 
//	{
//		assert ! values.isEmpty();
//		Log.i(LOGTAG, "updateValues... spreadsheet: "+spreadsheetId+" sheet: "+sheet);
//		Object res = null;
//		int numRows = values.size();
//		if (numRows == 0) {
//			return null;
//		}
//		int numCols = values.get(0).size();
//		// split? 		// Max rows: 1000, TODO max columns: 26 or expand the columns
//		// or do we need append? https://groups.google.com/g/google-spreadsheets-api/c/7fbHUmY3Tt4?pli=1
//		while(values.size() > MAX_ROWS_PER_UPDATE) {
//			List<List<Object>> slice = values.subList(0, MAX_ROWS_PER_UPDATE);
//			values = values.subList(MAX_ROWS_PER_UPDATE, values.size());
//			res = updateValues2(spreadsheetId, slice);			
//		}
//		if ( ! values.isEmpty()) {
//			res = updateValues2(spreadsheetId, values);
//		}
//		return res;
//	}
//		
//	Object updateValues2(String spreadsheetId, List<List<Object>> values)
//	{
//		try {
//			Sheets service = getService();
//			
//			ValueRange body = new ValueRange().setValues(values);
//	
//			String valueInputOption = "USER_ENTERED";
//			int w = values.get(0).size();
//			String c = getBase26(w - 1); // w base 26
//	
//			if (sheet !=null && sheet != 0) {
//				return updateValues3_specificSheet(spreadsheetId, values);
//			}
//			// ??if we had the specific-sheet title, could we set range using that??
//			String range = "A1:" + c + values.size();		
//			Update request = service.spreadsheets().values()
//					.update(spreadsheetId, range, body)
//					.setValueInputOption(valueInputOption);
//			
//			UpdateValuesResponse result = request.execute();
//			String ps = result.toPrettyString();
//			Integer cnt = result.getUpdatedCells();
//	//			System.out.println(ps);
//			Log.i(LOGTAG, "updated spreadsheet: "+getUrl(spreadsheetId));
//			return result.toPrettyString();
//		} catch(Exception ex) {
//			if (ex.toString().contains("exceeds grid limits")) {
//				throw new FailureException("Sorry: Try manually extending the sheet columns / rows: "+ex);
//			}
//			throw Utils.runtime(ex);
//		}
//	}
//	
//	private Object updateValues3_specificSheet(String spreadsheetId, List<List<Object>> values) throws GeneralSecurityException, IOException {
//		Log.d(LOGTAG, "updateValues3_specificSheet...");
//		List<Request> reqs = new ArrayList<Request>();
//		for(int r=0; r<values.size(); r++) {
//			List<Object> rowr = values.get(r);
//			for(int c=0; c<rowr.size(); c++) {
//
//				GridRange gr = new GridRange();
//				gr.setSheetId(sheet);
//				gr.setStartRowIndex(r);
//				gr.setEndRowIndex(r+1);
//				gr.setStartColumnIndex(c);
//				gr.setEndColumnIndex(c+1);
//				
//				CellData cd = new CellData();
//				ExtendedValue ev = new ExtendedValue();
//				Object vrc = rowr.get(c);
//				if (vrc==null) {
//					continue; // paranoia
//				}
//				if (vrc instanceof Number) {
//					ev.setNumberValue(((Number) vrc).doubleValue());
//				} else if (vrc.toString().startsWith("=")) {
//					ev.setFormulaValue(vrc.toString());
//				} else {
//					ev.setStringValue(vrc.toString());
//				}
//				Printer.out(ev.getStringValue());
//				cd = cd.setUserEnteredValue(ev);
//				Request req = new Request().setRepeatCell(
//						new RepeatCellRequest()
//						.setCell(cd)
//						.setRange(gr)
//						.setFields("userEnteredValue")
//						);			
//				reqs.add(req);
//			}
//		}
//		return doBatchUpdate(spreadsheetId, reqs);
//	}
//
//	/**
//	 * https://developers.google.com/sheets/api/samples/sheet
//	 * @throws IOException 
//	 */
//	public List<SheetProperties> getSheetProperties(String spreadsheetId) throws IOException {
//		Spreadsheet s = getSheet(spreadsheetId);
//		List<Sheet> sheets = s.getSheets();
//		List<SheetProperties> sprops = Containers.apply(sheets, Sheet::getProperties);
//		return sprops;		
//	}
//
//	/**
//	 * See https://developers.google.com/sheets/api/samples/formatting
//	 * @param spreadsheetId
//	 * @param rows 0 indexed, inclusive
//	 * @param cols
//	 * @param fg
//	 * @param bg
//	 * @return 
//	 * @return
//	 * @throws GeneralSecurityException
//	 * @throws IOException
//	 */
//	public Request setStyleRequest(IntRange rows, IntRange cols, java.awt.Color fg, java.awt.Color bg)
//			throws GeneralSecurityException, IOException 
//	{
//		Log.i(LOGTAG, "setStyle...");
//		GridRange gr = new GridRange();
//		if (sheet!=null) {
//			gr.setSheetId(sheet);
//		}
//		if (rows != null) {
//			gr.setStartRowIndex(rows.low);
//			if (rows.high<MAX_ROW) gr.setEndRowIndex(rows.high+1);
//		}
//		if (cols != null) {
//			gr.setStartColumnIndex(cols.low);
//			if (cols.high<MAX_COL) gr.setEndColumnIndex(cols.high+1);
//		}
//		float[] rgba = fg.getRGBComponents(null);
//		Color blu = new Color().setBlue(rgba[2]).setGreen(rgba[1]).setRed(rgba[0]).setAlpha(rgba[3]);
//		CellData cd = new CellData().setUserEnteredFormat(
//				new CellFormat().setTextFormat(
//				new TextFormat().setForegroundColor(blu)));
//		Request req = new Request().setRepeatCell(
//				new RepeatCellRequest()
//				.setCell(cd)
//				.setRange(gr)
//				.setFields("userEnteredFormat(textFormat)")
//				);
//		return req;
//	}
//	
//	public Object doBatchUpdate(String spreadsheetId, List<Request> reqs)
//			throws GeneralSecurityException, IOException 
//	{
//		Sheets service = getService();
//		BatchUpdateSpreadsheetRequest busr = new BatchUpdateSpreadsheetRequest()
//				.setRequests(reqs);
//		//		
////		ValueRange body = new ValueRange().setValues(values);
////
//		String valueInputOption = "USER_ENTERED";
////		int w = values.get(0).size();
////		String c = getBase26(w - 1); // w base 26
////		String range = "A1:" + c + values.size();
////		// TODO sheet number 
//		BatchUpdateSpreadsheetResponse result = service.spreadsheets().batchUpdate(
//				spreadsheetId, busr
//				)
////				.setFields()
////				.setValueInputOption(valueInputOption)
//				.execute();
//		String ps = result.toPrettyString();
//////			System.out.println(ps);
//		Log.i(LOGTAG, "styled spreadsheet: "+getUrl(spreadsheetId));
//		return result.toPrettyString();
//	}
//	
//	
//
//	public static String getUrl(String spreadsheetId) {
//		return "https://docs.google.com/spreadsheets/d/"+spreadsheetId;
//	}
//	
//	public static String getUrlCSV(String spreadsheetId) {
//		return "https://docs.google.com/spreadsheets/d/"+spreadsheetId+"/gviz/tq?tqx=out:csv";
//	}
//	
//	static Pattern gsu = Pattern.compile("^https://docs.google.com/spreadsheets/d/([^/]+)");
//	
//	static public String getSpreadsheetId(String url) {
//		// convert a normal url to a gviz
//		Matcher m = gsu.matcher(url);
//		if ( ! m.find()) {
//			return null;			
//		}
//		String docid = m.group(1);
//		return docid;
//	}
//
//	/**
//	 * 
//	 * @param w
//	 * @return 0 = A
//	 */
//	public static String getBase26(int w) {
//		assert w >= 0 : w;
//		int a = 'A';
//		if (w < 26) {
////			char c = (char) (a + w);
//			return Character.toString(a + w);
//		}
//		int low = w % 26;
//		int high = (w / 26) - 1; // -1 'cos this isnt true base 26 -- there's no proper 0 letter
//		return getBase26(high) + getBase26(low);
//	}
//	
//	public void clearSpreadsheet(String sid) throws IOException{
//		Log.d(LOGTAG, "clearSpreadsheet "+sid);
//		Sheets service = getService();
//		List<Request> requests = new ArrayList<>();
//
//		GridRange gr = new GridRange();
//		if (sheet !=null ) gr.setSheetId(sheet);
//		UpdateCellsRequest updateCellsReq = new UpdateCellsRequest()
//				.setRange(gr)
//				.setFields("*");
//		requests.add(new Request()
//				.setUpdateCells(updateCellsReq));
//		
//		BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);
//		BatchUpdateSpreadsheetResponse response = service.spreadsheets().batchUpdate(sid, body).execute();
//		Log.d(LOGTAG, "...clearSpreadsheet "+response);
//	}
//
//	/**
//	 * GoogleSheets doesn't handle null values well, 
//	 * ??what happens??
//	 * change all null values to an empty space
//	 * @param values
//	 * @return cleaned values
//	 */
//	public List<List<Object>> replaceNulls(List<List<Object>> values) {
//		List<List<Object>> cleanedValues = new ArrayList();
//		for(List<Object> row : values) {
//			Collections.replaceAll(row, null, " ");
////			Collections.replaceAll(row, "", " "); ??wanted??
//			cleanedValues.add(row);
//		}
//		return cleanedValues;
//	}
//
//	public List<List<Object>> getData(String spreadsheetId, String a1Range, String TODOoptionalSheetId) throws IOException {
//		Sheets service = getService();
//		ValueRange d = service.spreadsheets().values().get(spreadsheetId, a1Range).execute();
//		return d.getValues();
//	}	
//
//}

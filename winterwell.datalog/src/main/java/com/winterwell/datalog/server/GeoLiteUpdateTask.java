package com.winterwell.datalog.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.winterwell.utils.Proc;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.LoginDetails;
import com.winterwell.web.app.Logins;


/**
 * Updater for our GeoLite2 IP-geolocation CSVs.
 * Contacts MaxMind servers, checks timestamp on latest available bundle file,
 * and if it's newer than the current one downloads it, extracts relevant CSVs,
 * and instructs GeoLiteLocator to refresh.
 * TODO Restructure this to call from inside DataLogServer on a timer
 * @author roscoe
 *
 */
public class GeoLiteUpdateTask extends TimerTask {
	private static final String LOGTAG = "GeoLiteUpdateTask";
	
	GeoLiteLocator geoLiteLocator;
	
	/** Replace $DATABASE and $LICENSE_KEY to use */
	static final String BASE_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City-CSV&license_key=$LICENSE_KEY";
	
	static final String CSV_URL = BASE_URL + "&suffix=zip"; 
	static final String CHECKSUM_URL = BASE_URL + "&suffix=zip.sha256";
	
	// We retain the downloaded CSV zip here
	static final String ZIP_NAME = "geolite2_csv.zip";
	
	
	// For extracting the path to the block and location files from the downloaded zip's directory listing
	static final Pattern BLOCKS_FILENAME_PATTERN = Pattern.compile("^.+" + GeoLiteLocator.BLOCKS_CSV_NAME + "$", Pattern.MULTILINE);
	static final Pattern LOCNS_FILENAME_PATTERN = Pattern.compile("^.+" + GeoLiteLocator.LOCATIONS_CSV_NAME + "$", Pattern.MULTILINE);
	
	static final Pattern HASH_PATTERN = Pattern.compile("[\\da-f]+");
	
	/** Was the update attempt a success? If it didn't work, should we try it again? */
	private class UpdateResult {
		boolean success;
		boolean shouldRetry;
		public UpdateResult(boolean success, boolean shouldRetry) {
			this.success = success;
			this.shouldRetry = shouldRetry;
		}
	}
	
	
	public GeoLiteUpdateTask(GeoLiteLocator gll) {
		this.geoLiteLocator = gll;
	}
	
	
	@Override
	public void run() {
		// Try to acquire lock, no need to wait if another thread is already updating
		if (!GeoLiteLocator.updateLock.tryLock()) {
			Log.w(LOGTAG, "Concurrent updates attempted, stopping this update attempt.");
			return;
		}
		
		try {
			UpdateResult ur = null; 
			int retries = 0;
			
			// Quick start: Try to construct prefix tree with existing files
			// Failure here is fine - don't make a fuss until we see a failure with up-to-date files.
			if (geoLiteLocator.prefixes == null) {
				try {
					File locnsFile = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.LOCATIONS_CSV_NAME);
					File blocksFile = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.BLOCKS_CSV_NAME);
					geoLiteLocator.constructPrefixTree(locnsFile, blocksFile);
				} catch (Exception e) {}
			}
			
			// Try to get updated files
			while (ur == null || (!ur.success && (ur.shouldRetry && retries < 2))) {
				ur = run2_inLock();
				retries++;
			}
			
			// Something went wrong? Log failure mode.
			if (!ur.success) {
				if (ur.shouldRetry) {
					Log.e(LOGTAG, "Failed to update GeoLiteLocator due to possibly-transient errors, gave up after " + retries + " attempts.");
				} else {
					Log.e(LOGTAG, "Failed to update GeoLiteLocator due to permanent failure, please check CSV format and parsing code.");
				}
			}
		} finally {
			GeoLiteLocator.updateLock.unlock();
		}
	}
	
	
	private UpdateResult run2_inLock() {
		Log.d(LOGTAG, "Checking for fresh GeoLite2 CSV...");
		
		// Get our MaxMind license key from the logins file...
		LoginDetails ld = Logins.get("maxmind.com");
		if (ld == null || Utils.isBlank(ld.apiSecret)) {
			// Probably just means this server's logins repo is behind, but make sure someone knows
			Log.e(LOGTAG, "Can't run: no apiSecret entry for maxmind.com in misc logins file?");
			return new UpdateResult(false, false); // Bad config, no point retrying.
		}
		String csvUrl = CSV_URL.replace("$LICENSE_KEY", ld.apiSecret);
		
		File zipFile = new File(GeoLiteLocator.GEOIP_FILES_PATH, ZIP_NAME);
		
		try {
			// Check the new CSV bundle's Last-Modified header...
			HttpURLConnection conn = (HttpURLConnection) (new URL(csvUrl).openConnection());
			conn.setRequestMethod("HEAD");
			conn.connect();
			Long newVersionTimestamp = conn.getHeaderFieldDate("Last-Modified", 0);
			conn.disconnect();
			// ...and compare it to the timestamp of the current version.
			if (zipFile.exists() && zipFile.lastModified() >= newVersionTimestamp) {
				Log.d(LOGTAG, "Current GeoLite2 is up to date.");
				return new UpdateResult(true, false); // Success!
			}
		} catch (Exception e) {
			// ProtocolException shouldn't happen here but IOException might 
			return new UpdateResult(false, true); // Could be transient connection problem, do retry
		}
		
		Log.d(LOGTAG, "Newer GeoLite2 data available, downloading...");

		// Time to update the CSVs!
		// Make sure ./geoip exists
		File geoipDir = new File(GeoLiteLocator.GEOIP_FILES_PATH);
		if (!geoipDir.exists()) geoipDir.mkdirs();
		
		// Now let's download the new zip file.
		FakeBrowser fb = new FakeBrowser();
		fb.setMaxDownload(-1); // File is ~45MB, default 10MB limit throws exception
		File tmpFile = fb.getFile(csvUrl);
		
		// Compare reference SHA256 to that of downloaded file
		String checksumUrl = CHECKSUM_URL.replace("$LICENSE_KEY", ld.apiSecret);
		String referenceHash = fb.getPage(checksumUrl);
		String downloadedHash = Proc.run("sha256sum " + tmpFile.getPath());
		Matcher refHashMatcher = HASH_PATTERN.matcher(referenceHash);
		refHashMatcher.find();
		if (!downloadedHash.contains(refHashMatcher.group())) {
			Log.e(LOGTAG, "Downloaded GeoLite2 zip didn't match reference SHA256 hash, aborting");
			tmpFile.delete();
			return new UpdateResult(false, true); // Maybe corrupted download, do retry
		}
		
		Log.d(LOGTAG, "Downloaded GeoLite2 zip successfully. Extracting files...");
		
		// What do we want to extract from the zip?
		String blocksPath = "";
		String locnsPath = "";
		try {
			// Print the file paths contained in the new zip and find the paths to the block and location files
			String zipContents = Proc.run("unzip -Z -1 " + tmpFile.getPath()); // -Z = list contents, -1 = 1 line per file
			Matcher findBlocksPath = BLOCKS_FILENAME_PATTERN.matcher(zipContents);
			Matcher findLocnsPath = LOCNS_FILENAME_PATTERN.matcher(zipContents);
			findBlocksPath.find();
			findLocnsPath.find();
			blocksPath = findBlocksPath.group();
			locnsPath = findLocnsPath.group();	
		} catch (Exception e) {
			// This probably means the ZIP file content layout has changed & we need to rewrite.
			Log.e(LOGTAG, "Couldn't find expected files in new GeoLite2 zip");
			tmpFile.delete();
			return new UpdateResult(false, false); // don't expect a different result next time
		}
		

		// Unzip the needed files only to a temp directory
		File newCsvDir = new File(GeoLiteLocator.GEOIP_FILES_PATH, "newcsv");
		if (newCsvDir.exists()) {
			FileUtils.deleteDir(newCsvDir); // Ensure no previous dir lying around from a failed run
		}
		newCsvDir.mkdir();
		// -j flag = ignore zip directory structure and extract to specified dir
		Proc.run("unzip -j \"" + tmpFile.getPath()  + "\" \"" + blocksPath + "\" -d \"" + newCsvDir.getPath() + "\"");
		Proc.run("unzip -j \"" + tmpFile.getPath() + "\" \"" + locnsPath + "\" -d \"" + newCsvDir.getPath() + "\"");
		// Grab unzipped files...
		File blocksTemp = new File(newCsvDir, GeoLiteLocator.BLOCKS_CSV_NAME);
		File locnsTemp = new File(newCsvDir, GeoLiteLocator.LOCATIONS_CSV_NAME);
		
		Log.d(LOGTAG, "Extracted CSV files. Constructing prefix tree...");
		
		try {
			// OK, try to parse those CSVs
			geoLiteLocator.constructPrefixTree(locnsTemp, blocksTemp);
			
			// No errors! Overwrite the previous version zip and CSV files with the new ones.
			// FileUtils.move() will overwrite so no need to check dest
			File blocksDest = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.BLOCKS_CSV_NAME);
			File locnsDest = new File(GeoLiteLocator.GEOIP_FILES_PATH, GeoLiteLocator.LOCATIONS_CSV_NAME);
			FileUtils.move(blocksTemp, blocksDest);
			FileUtils.move(locnsTemp, locnsDest);
			FileUtils.move(tmpFile, zipFile);
			Log.d("Success! GeoLiteLocator has been updated.");
		} catch (FileNotFoundException e) {
			Log.e(LOGTAG, "Downloaded new GeoLite2 ZIP but missing expected file: " + e);
			return new UpdateResult(false, true); // don't expect a different result next time
		} catch (ParseException e) {
			Log.e(LOGTAG, "Downloaded new GeoLite2 ZIP but found unexpected CSV headers:" + e);
			return new UpdateResult(false, true); // don't expect a different result next time
		} finally {
			// Clean up any files still left
			FileUtils.deleteDir(newCsvDir);
		}
		return new UpdateResult(true, false);
	}
	
}

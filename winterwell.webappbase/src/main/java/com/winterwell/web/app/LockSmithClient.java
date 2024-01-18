package com.winterwell.web.app;

import java.util.concurrent.Future;

import com.google.common.util.concurrent.Futures;
import com.winterwell.utils.time.Dt;
import com.winterwell.web.data.XId;

/**
 * Access LockSmith
 * @author daniel
 * @testedby LockSmithClientTest
 */
public class LockSmithClient {

	String ENDPOINT = "https://locksmith.good-loop.com/lock"; 
			
	XId oxid;
	
	public LockSmithClient(XId oxid) {
		this.oxid = oxid;
		// TODO auth (for now you cant do anything nasty with locks, so operate on trust)
	}
	
	public Future<Object> getLock(String lockId, Dt patience, Dt holdFor) {
		// TODO !
		return Futures.immediateFuture("Dummy Lock for "+lockId);
	}
	
	public Future<Object> tryGetLock(String lockId, XId oxid, Dt patience, Dt holdFor) {
		// TODO !
		return Futures.immediateFuture("Dummy Lock for "+lockId);
	}
	
	public void releaseLock(String lockId) {
		// TODO
	}
}

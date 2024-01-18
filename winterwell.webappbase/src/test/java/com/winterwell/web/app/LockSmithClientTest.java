package com.winterwell.web.app;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Test;

import com.winterwell.utils.time.TUnit;
import com.winterwell.web.data.XId;

public class LockSmithClientTest {

	@Test
	public void testGetLock() throws InterruptedException, ExecutionException {
		LockSmithClient lsc = new LockSmithClient(new XId("test@good-loop.com@email"));
		String lockId = "test/GetLock";
		Future<Object> fgot = lsc.getLock(lockId, TUnit.MINUTE.dt, TUnit.MINUTE.dt);
		System.out.println(fgot.get());
		assert ! fgot.get().toString().contains("Dummy") : "TODO";
	}

}

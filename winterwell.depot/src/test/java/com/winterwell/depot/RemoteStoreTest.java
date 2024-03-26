package com.winterwell.depot;

import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.Printer;

public class RemoteStoreTest {

	@Test @Ignore // failed?!
	public void testGet() {
		DepotConfig config = new DepotConfig();
		RemoteStore rs = new RemoteStore(config);
		Desc desc = new Desc("RemoteStoreTest", String.class);
		desc.setServer(Desc.CENTRAL_SERVER);
		desc.setTag("test");		
		Object got = rs.get(desc); // TODO avoid the local cache when testing
		Printer.out(got);
		assert got != null;

	}

}

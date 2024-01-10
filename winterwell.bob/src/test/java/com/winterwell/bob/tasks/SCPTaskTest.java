package com.winterwell.bob.tasks;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.winterwell.utils.Utils;
import com.winterwell.utils.io.FileUtils;

public class SCPTaskTest {


//	@Test// not that good - Linux Mint 2020
//	public void testWhoIs() throws SocketException, IOException {
//		WhoisClient whois = new WhoisClient();
//      whois.connect(WhoisClient.DEFAULT_HOST);
//      System.out.println(whois.query("82.37.168.255"));
//      whois.disconnect();
//	}
	
	@Test
	public void testBadRun() throws IOException {
		{
			File f = File.createTempFile("test", ".txt");
			File f2 = File.createTempFile("test", ".txt");
			FileUtils.delete(f);
			FileUtils.delete(f2);
			try {
				// Task construction now asserts local existence
				SCPTask task = new SCPTask(f, "localhost", f2.getAbsolutePath());
				task.run();
				assert false;
			} catch (Error e) {
				// good
			}
		}
	}

	
	// fails on permissions
	public void testRun() throws IOException {
		File f = File.createTempFile("test", ".txt");
		File f2 = File.createTempFile("test", ".txt");
		FileUtils.delete(f2);
		FileUtils.write(f, "Hello World");
		SCPTask task = new SCPTask(f, "localhost", f2.getAbsolutePath());
		task.run();

		String txt = FileUtils.read(f2);
		assert txt.equals("Hello World");

		FileUtils.delete(f2);
		FileUtils.delete(f);
	}

	

	@Test
	public void testWithEgan() throws IOException {
		SCPTask._atomic = true;
		String salt = Utils.getRandomString(4);
		File f = new File("test-out", "SCPTest_"+salt+".txt");
		f.getParentFile().mkdirs();
		FileUtils.write(f, "Hello World "+salt);
		SCPTask task = new SCPTask(f, "winterwell@egan.soda.sh", "/home/winterwell/test/SCPTest_"+salt+".txt");
		task.run();
		
		File f2 = new File("test-out", "SCPTest2_"+salt+".txt");
		SCPTask task2 = new SCPTask("winterwell@egan.soda.sh", "/home/winterwell/test/SCPTest_"+salt+".txt", f2);
		task2.run();

		String txt = FileUtils.read(f2);
		assert txt.equals("Hello World "+salt) : txt;

//		// clean up locally
//		FileUtils.delete(f2);
//		FileUtils.delete(f);
	}
	
	
	// fails on permissions
	public void testCreateTargetDir() throws IOException {
		File f = File.createTempFile("test", ".txt");
		File f2 = new File("foo/bar.txt");
		FileUtils.delete(f2);
		FileUtils.write(f, "Hello World");
		SCPTask task = new SCPTask(f, "localhost", f2.getAbsolutePath());
		task.run();

		String txt = FileUtils.read(f2);
		assert txt.equals("Hello World");

		FileUtils.deleteDir(new File("foo"));
		FileUtils.delete(f);
	}
}

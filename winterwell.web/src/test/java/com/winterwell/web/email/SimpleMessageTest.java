package com.winterwell.web.email;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.Printer;
import com.winterwell.utils.gui.GuiUtils;

public class SimpleMessageTest {

	@Test @Ignore // needs a user
	public void testLoadFromFile() {
		File a = new File(FileUtils.getUserDirectory(), "Downloads");
		if ( ! a.isFile()) a = null;
		File emlFile = GuiUtils.selectFile("Pick an .eml file", a, f -> f.isDirectory() || f.getName().endsWith(".eml"));
		
		SimpleMessage loaded = SimpleMessage.loadFromFile(emlFile);
		String body = loaded.getBodyHtml();
		Printer.out(body);
		Printer.out("\n\n--------------------------------------------\n\n");
		Printer.out(loaded.getBodyText());
	}

}

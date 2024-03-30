package com.winterwell.web.app;

import java.io.File;
import java.util.Properties;

import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.Dep;
import com.winterwell.utils.Printer;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.web.email.EmailConfig;
import com.winterwell.web.email.SimpleMessage;

import jakarta.mail.internet.InternetAddress;

/**
 * @author daniel
 *
 */
@Ignore // needs internet
public class EmailerTest {

/**
 * copy pasta from ChatRoundabout
 * @return
 */
	protected Emailer init3_emailer() {
		if (Dep.has(Emailer.class)) {
			return Dep.get(Emailer.class);		
		}
		File propsFile = Logins.getLoginFile("google.good-loop.com", "email.properties");
		Properties props = FileUtils.loadProperties(propsFile);		
			
		EmailConfig ec = new EmailConfig();
		ec.emailServer = props.getProperty("emailServer").trim();
		ec.emailFrom = props.getProperty("emailFrom").trim();
		ec.emailPassword = props.getProperty("emailPassword").trim();
		ec.emailPort = 465;
		ec.emailSSL = true;		
		Emailer e = new Emailer(ec);
		Dep.set(Emailer.class, e);
		return e;
	}
	
	@Test
	public void testSendAnEmail() throws Exception {
		Emailer e = init3_emailer();		
		InternetAddress to = new InternetAddress("daniel.winterstein@gmail.com");
		SimpleMessage email = new SimpleMessage(e.getFrom(), to, "Test hello from Emailer", 
				"Hello Daniel :)");
		e.send(email);
	}
	

	@Test
	public void testSendAttachment() throws Exception {
		Emailer e = init3_emailer();
		InternetAddress to = new InternetAddress("daniel.winterstein@gmail.com");
		SimpleMessage email = new SimpleMessage(e.getFrom(), to, "Test hello + Attachment from Emailer", 
				"Hello Daniel :)");
		File f = FileUtils.createTempFile("testEmailer", ".csv");
		Printer.out(f.getAbsoluteFile());
		FileUtils.write(f, "Name, Message\nAlice, Hello\nBob, Hi");
		email.addAttachment(f);
		e.send(email);
	}

}

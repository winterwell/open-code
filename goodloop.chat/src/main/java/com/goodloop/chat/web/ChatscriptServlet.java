package com.goodloop.chat.web;

import com.goodloop.chat.data.Chatscript;
import com.winterwell.web.app.CrudServlet;

/**
 * Status: not used!
 * @author daniel
 *
 */
public class ChatscriptServlet extends CrudServlet<Chatscript> {

	public ChatscriptServlet() {
		super(Chatscript.class);
	}

}

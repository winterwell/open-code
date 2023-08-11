package com.goodloop.chat.web;

import com.goodloop.chat.data.Chat;
import com.winterwell.web.app.CrudServlet;

/**
 * Status: not used!
 * @author daniel
 *
 */
public class ChatServlet extends CrudServlet<Chat> {

	public ChatServlet() {
		super(Chat.class);
	}

}

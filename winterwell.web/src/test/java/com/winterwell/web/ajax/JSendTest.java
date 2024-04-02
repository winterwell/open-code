package com.winterwell.web.ajax;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import com.winterwell.utils.containers.Pair;

import junit.framework.Assert;

public class JSendTest {

	@Test
	public void testToJSONString() {
		String js1 = new JSend<>().setStatus(KAjaxStatus.error).toJSONString();		
//		System.out.println(js1);
		
		String js2 = new JSend<>(new Pair(1,2)).toJSONString();
//		System.out.println(js2);
		assert js2.contains("\"data\":[1,2]");
		
		String js = "{\n\t\"a\":1\n}";
		String js3 = new JSend().setData(new JThing<>().setJson(js)).toJSONString();
//		System.out.println(js3);
		assert js3.contains(js);
	}

}

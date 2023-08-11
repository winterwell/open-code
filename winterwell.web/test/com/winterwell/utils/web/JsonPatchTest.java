package com.winterwell.utils.web;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.winterwell.gson.Gson;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;

public class JsonPatchTest {


	@Test
	public void testAddIntoEmpty() {
		Map before = new ArrayMap("a", new ArrayList());
		JsonPatch ops = JsonPatch.fromJson(Arrays.asList(new ArrayMap(
					"op","add",
					"path","/a/0/fruit", 
					"value","apple")
				));
		JsonPatchOp jop = ops.diffs.get(0);
		
		Map after2 = new ArrayMap(before);
		ops.apply(after2);
//		System.out.println(after2);
		String jafter = Gson.toJSON(after2);
		assertEquals("{'a':[{'fruit':'apple'}]}".replace('\'', '"'),jafter);
		
		assert ! ops.getModifiedDiff4diff().isEmpty();
		System.out.println(ops.getModifiedDiff4diff());
//		System.out.println(Gson.toJSON(ops.getModifiedDiff4diff()));
		// TODO we'd rather have value:[] 
//		assertEquals("[{\"op\":\"add\",\"path\":\"/a/0\",\"value\":{}}]", Gson.toJSON(ops.getModifiedDiff4diff()));
	}

	

	@Test
	public void testAddIntoEmptyArray() {
		Map before = new ArrayMap("a", Arrays.asList());
		JsonPatch ops = JsonPatch.fromJson(Arrays.asList(new ArrayMap(
					"op","add",
					"path","/a/0/fruit", 
					"value","apple")
				));
		JsonPatchOp jop = ops.diffs.get(0);
		
		Map after2 = new ArrayMap(before);
		ops.apply(after2);
//		System.out.println(after2);
		String jafter = Gson.toJSON(after2);
		assertEquals("{'a':[{'fruit':'apple'}]}".replace('\'', '"'),jafter);
		
		System.out.println(ops.getModifiedDiff4diff());
		System.out.println(ops.getDiffs());				
		assert ! ops.getModifiedDiff4diff().isEmpty();
	}

	
	
	@Test
	public void testArrayNull() {
		Map before = new ArrayMap("a", Arrays.asList("Apple","Avocado"));
		JsonPatch ops = JsonPatch.fromJson(Arrays.asList(new ArrayMap(
					"op","remove",
					"path","/a/1")
				));
		JsonPatchOp jop = ops.diffs.get(0);
		
		Map after2 = new ArrayMap(before);
		ops.apply(after2);
		System.out.println(after2);
		Map after = new ArrayMap("a", Arrays.asList("Apple"));
		assert Containers.same(after2, after);
	}



	@Test
	public void testRemoveTopLevel() {
		Map before = new ArrayMap("a", Arrays.asList("Apple","Avocado"), "b", "banana");
		JsonPatch ops = JsonPatch.fromJson(Arrays.asList(new ArrayMap(
					"op","remove",
					"path","/b")
				));
		JsonPatchOp jop = ops.diffs.get(0);
		
		Map after2 = new ArrayMap(before);
		ops.apply(after2);
		System.out.println(after2);
		Map after = new ArrayMap("a", Arrays.asList("Apple","Avocado"));
		assert Containers.same(after2, after);
	}

	@Test
	public void testSimple() {
		Map before = new ArrayMap("a", "Apple");
		Map after = new ArrayMap("b", "Banana");
		JsonPatch jp = new JsonPatch(before, after);
		System.out.println(jp.toJson2());
		
		Map after2 = new ArrayMap(before);
		jp.apply(after2);
		System.out.println(after);
		System.out.println(after2);
		assert Containers.same(after2, after);
	}
	
	@Test
	public void testSimpleList() {
		List before = Arrays.asList("a", "Apple");
		List after = Arrays.asList("a", "Banana");
		JsonPatch jp = new JsonPatch(before, after);
		System.out.println(jp.toJson2());
		
		List after2 = new ArrayList(before);
		jp.apply(after2);
		System.out.println(after);
		System.out.println(after2);
		assert Containers.same(after2, after);
	}
	
	@Test
	public void testMaps2Deep() {
		Map before = new ArrayMap("a", new ArrayMap("fruit", "Apple"), "b", new ArrayMap("fruit", "Breadfruit"), "c", "carrot", "d", "dog");
		Map after = new ArrayMap("a", new ArrayMap("animal", "Antelope"), "b", new ArrayMap("fruit", "Banana"), "c", "carrot", "d", "date");
		JsonPatch jp = new JsonPatch(before, after);
		System.out.println(jp.toJson2());
		
		Map after2 = new ArrayMap(before);
		jp.apply(after2);
		System.out.println(after);
		System.out.println(after2);
		assert Containers.same(after2, after);
	}

}

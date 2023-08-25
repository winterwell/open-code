package com.winterwell.nlp.query;

import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.winterwell.utils.Mutable;
import com.winterwell.utils.Mutable.Int;
/**
 * NB in webappbase project cos so is searchquery
 * @author mark
 *
 */
public class SearchQueryTest {

	@Test @Ignore // currently fails -- not has some special case handling (for dash) Bleurgh
	// workaround - use brackets
	public void testNotProperty() {	
		String s = "-foo:bar";
		SearchQuery sq = new SearchQuery(s);
		System.out.println(sq.getParseTree());
		assert "[and, [-, {foo=bar}]]".equals(sq.getParseTree().toString());
	}
	
	@Test @Ignore
	public void testMessyOrAnd() {
		String s = "rate:any OR dynamic:true AND campaign:foo";
		SearchQuery sq = new SearchQuery(s);
		System.out.println(sq.getParseTree());
		assert "[and, [or, {foo=any}, {dynamic:true}], {campaign:foo}]".equals(sq.getParseTree().toString());		
	}
	
	@Test
	public void testNotPropertyBrackets() {	
		String s = "-(foo:bar)";
		SearchQuery sq = new SearchQuery(s);
		System.out.println(sq.getParseTree());
		assert "[-, {foo=bar}]".equals(sq.getParseTree().toString());
	}
	

	@Test
	public void testNotGlobProperty() {	
		String s = "apple -(foo:*bar)";
		SearchQuery sq = new SearchQuery(s);
		System.out.println(sq.getParseTree());
		assert "[and, apple, [-, {foo=*bar}]]".equals(sq.getParseTree().toString());
	}
	
	@Test
	public void testGlobProperty() {
		String s = "foo:*bar";
		SearchQuery sq = new SearchQuery(s);
		System.out.println(sq.getParseTree());
		assert "[and, {foo=*bar}]".equals(sq.getParseTree().toString());
	}


	@Test
	public void testValueWithColon() {
		String q = "segment:\"T4G:register\"";
		SearchQuery sq = new SearchQuery(q);
		List ptree = sq.getParseTree();
		System.out.println(ptree.toString());
		assert ptree.toString().contains("T4G:register");
	}

	
	/**
	 * Use case: to query into a json object
	 */
	@Test
	public void testNestedPropertyKey() {
		{
			String s = "foo:bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
			assert "[and, {foo=bar}]".equals(sq.getParseTree().toString());
		}
		{
			String s = "abba.foo:bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
			assert "[and, {abba.foo=bar}]".equals(sq.getParseTree().toString());
		}
	}
	
	/**
	 * test currency conversion keys 
	 */
	@Test
	public void testCAPSKey() {
		{
			String s = "chf:bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
			assert "[and, {chf=bar}]".equals(sq.getParseTree().toString());
		}
		{
			String s = "chf2usd:bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
			assert "[and, {chf2usd=bar}]".equals(sq.getParseTree().toString());
		}
		{
			String s = "CHF2USD:bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
			assert "[and, {CHF2USD=bar}]".equals(sq.getParseTree().toString());
		}
	}
	
	
	@Test
	public void testNestedPropertyValue() {
		{
			String s = "due:before:today";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
			assert "[and, {due={before=today}}]".equals(sq.getParseTree().toString());
		}
	}

	@Test
	public void testQuoteDash() {
		{
			String s = "foo bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
		}
		{	// TODO check we don't break parsing of NOT 
			String s = "foo -bar";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree()); // TODO should be NOT bar
		}
		{
			String s = "foo - bar"; // Keep dash inside a word
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
		}
		{
			String s = "fo-o bar -baz";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
		}
		{
			String s = "foo (bar)";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
		}
		{
			String s = "Test Advert - With";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq.getParseTree());
		}
	}

	@Test
	public void testQuotePropSimple() {
		SearchQuery sq2b = new SearchQuery("mykey:\"value two!\"");
		List pt = sq2b.getParseTree();
		System.out.println(pt.toString());
		assert pt.toString().equals("[and, {mykey=[\", value two!]}]") : pt.toString();			
	}
	
	@Test
	public void testMalformedKeyValue() {
		SearchQuery sq2b = new SearchQuery("mybadkey:");
		List pt = sq2b.getParseTree();
		System.out.println(pt.toString());
		assert pt.toString().equals("[and, mybadkey:]") : pt.toString();			
	}
	
	@Test
	public void testQuotePropOr() {
		{
			SearchQuery sq2b = new SearchQuery("mykey:\"v 1\" OR mykey:v2");
			List pt = sq2b.getParseTree();
			System.out.println(pt.toString());
			assert pt.toString().equals("[or, {mykey=[\", v 1]}, {mykey=v2}]") : pt.toString();			
		}
		{
			SearchQuery sq2b = new SearchQuery("mykey:v1 OR mykey:\"value two!\"");
			List pt = sq2b.getParseTree();
			System.out.println(pt.toString());
			assert pt.toString().equals("[or, {mykey=v1}, {mykey=[\", value two!]}]") : pt.toString();			
		}
		{
			SearchQuery sq = new SearchQuery("foo");
			SearchQuery sq2 = sq.withPropOr("mykey", Arrays.asList("v1","value two!"));
			String sq2s = sq2.getRaw();
			assert sq2s.equals("foo (mykey:v1 OR mykey:\"value two!\")") : sq2s;
			SearchQuery sq2b = new SearchQuery(sq2s);
			List pt = sq2b.getParseTree();
			System.out.println(pt.toString());
			assert pt.toString().equals("[and, foo, [or, {mykey=v1}, {mykey=[\", value two!]}]]") : pt.toString();
		}
	}

		
	@Test
	public void testQuoteBug() {
		{
			String s = "\"against-malaria-foundation\"";
			SearchQuery sq = new SearchQuery(s);
			System.out.println(sq);
			assert sq.matches("against-malaria-foundation");
			assert ! sq.matches("foundation against malaria");
		}
	}
	
	
	@Test
	public void testParse3_nextWord() {
		{
			String s = "some words here";
			SearchQuery sq = new SearchQuery(s);
			Int index = new Mutable.Int();
			String next = sq.parse3_nextWord(s, index);
			String next2 = sq.parse3_nextWord(s, index);
			String next3 = sq.parse3_nextWord(s, index);
			String next4 = sq.parse3_nextWord(s, index);
			assert next.equals("some") : next;
			assert next2.equals("words") : next;
			assert next4.isEmpty() : next4;
		}
		{
			String s = "(blue OR green)";
			SearchQuery sq = new SearchQuery(s);
			Int index = new Mutable.Int();
			String next1 = sq.parse3_nextWord(s, index);
			String next2 = sq.parse3_nextWord(s, index);
			String next3 = sq.parse3_nextWord(s, index);
			assert next1.equals("(") : next1;
			assert next2.equals("blue") : next1;
		}
		{	// hack handling of :s  
			String s = "name:dan";
			SearchQuery sq = new SearchQuery(s);
			Int index = new Mutable.Int();
			String next = sq.parse3_nextWord(s, index);
			assert next.equals("name:") : next;
			String next2 = sq.parse3_nextWord(s, index);
			assert next2.equals("dan") : next2;
		}
		{	// hack handling of :s and "s 
			String s = "name:\"Dan W\"";
			SearchQuery sq = new SearchQuery(s);
			Int index = new Mutable.Int();
			String next = sq.parse3_nextWord(s, index);
			String next2 = sq.parse3_nextWord(s, index);
			String next3 = sq.parse3_nextWord(s, index);
			assert next.equals("name:") : next;
			assert next2.equals("Dan W") : next;
		}
		{
			String s = "due:before:2020-01-01";
			SearchQuery sq = new SearchQuery(s);
			Int index = new Mutable.Int();
			String next = sq.parse3_nextWord(s, index);
			String next2 = sq.parse3_nextWord(s, index);
			String next3 = sq.parse3_nextWord(s, index);
			assert next.equals("due:") : next;
			assert next2.equals("before:") : next;
			assert next3.equals("2020-01-01") : next;
		}	
	}

	@Test
	public void testDueBefore() {
		{
			String s = "due:before:2020-01-01";
			SearchQuery sq = new SearchQuery(s);
			List pt = sq.getParseTree();
			String pts = pt.toString();
			assert pts.equals("[and, {due={before=2020-01-01}}]") : pts;
		}
	}
	
	@Test
	public void testParseTreeAndOr() {
		{
			String s = "(Andris OR AU) AND holiday";
			SearchQuery sq = new SearchQuery(s);
			List pt = sq.getParseTree();
			String pts = pt.toString();
			assert pts.equals("[and, [or, Andris, AU], holiday]") : pts;
		}
		{
			String s = "(Andris OR AU) AND holiday";
			SearchQuery sq = new SearchQuery(s);
			sq.setCanonicaliseText(true);
			List pt = sq.getParseTree();
			String pts = pt.toString();
			assert pts.equals("[and, [or, andris, au], holiday]") : pts;
		}
	}
	
	@Test
	public void testIdWithSpace() {
		{	// keyword is fine
			SearchQuery sq = new SearchQuery("campaign:all_fine-here");
			System.out.println(sq.getParseTree());	
			assert sq.getParseTree().toString().equals(
					"[and, {campaign=all_fine-here}]");
		}
		{	// quote marks work
			SearchQuery sq = new SearchQuery("campaign:\"smart william\"");
			System.out.println(sq.getParseTree());	
			assert sq.getParseTree().toString().equals(
					"[and, {campaign=[\", smart william]}]");
		}
		{	// quote marks are harmless
			SearchQuery sq = new SearchQuery("campaign:\"alice\"");
			System.out.println(sq.getParseTree());	
			assert sq.getParseTree().toString().equals(
					"[and, {campaign=[\", alice]}]");
		}
		{	// no quotes -> spaces break stuff!
			SearchQuery sq = new SearchQuery("campaign:silly billy");
			System.out.println(sq.getParseTree());	
			assert sq.getParseTree().toString().equals(
					"[and, {campaign=silly}, billy]");
		}
	}
	
	@Test
	public void testCrudListWIthId() {		
		SearchQuery sq = new SearchQuery("eventId:5eBoOIPa").setCanonicaliseText(false);
		System.out.println(sq.getParseTree());	
		assert sq.getParseTree().toString().equals(
				"[and, {eventId=5eBoOIPa}]");
	}
	
	@Test
	public void testCaseSensitive() {		
		SearchQuery sq = new SearchQuery("Hello").setCanonicaliseText(false);
		assert ! sq.matches("hello");
		assert sq.matches("Hello");	
		assert ! sq.matches("héllo");				
	}
	
	@Test
	public void testCaseInSensitive() {		
		SearchQuery sq = new SearchQuery("Hello").setCanonicaliseText(true);
		assert sq.matches("hello");
		assert sq.matches("Hello");	
		assert sq.matches("héllo");				
	}
	
	@Test
	public void testBOASBugNov2018() {
		String q = "evt:donation vert:Q7X1VA5c bid:unset";
		SearchQuery sq = new SearchQuery(q);
		System.out.println(sq);
		System.out.println(sq.getParseTree());
		assert sq.getParseTree().toString().equals("[and, {evt=donation}, {vert=Q7X1VA5c}, {bid=unset}]");
	}
	
	@Test
	public void testKeyNull() {
		{
			SearchQuery sq = new SearchQuery("alice foo:null");
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, alice]") : sq;
			assert sq.matches("Hello alice :)");
		}
		{
			SearchQuery sq = new SearchQuery("foo:null alice");
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, alice]") : sq;
			assert sq.matches("Hello alice :)");
		}

		{
			SearchQuery sq = new SearchQuery("name:null foo:null");
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and]") : pt;
			assert sq.matches("Hello alice :)");
		}
		{
			SearchQuery sq = new SearchQuery("alice name:null foo:null bob");
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, alice, bob]") : pt.toString();
		}
	}

	
	@Test
	public void testAndOr() {
		{
			String q = "(alice OR anne) AND (bob OR ben)";
			SearchQuery sq = new SearchQuery(q);
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, [or, alice, anne], [or, bob, ben]]");
		}
		{
			String q = "(name:alice OR anne) AND bob";
			SearchQuery sq = new SearchQuery(q);
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, [or, {name=alice}, anne], bob]");
		}
		{
			String q = "(alice OR anne) AND (name:bob OR ben)";
			SearchQuery sq = new SearchQuery(q);
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, [or, alice, anne], [or, {name=bob}, ben]]");
		}
		{
			String q = "(cid:veg OR cid:fruit) AND spend";
			SearchQuery sq = new SearchQuery(q);
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, [or, {cid=veg}, {cid=fruit}], spend]");
		}
		{
			String q = "(cid:veg OR cid:fruit) AND (spend OR evt)";
			SearchQuery sq = new SearchQuery(q);
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, [or, {cid=veg}, {cid=fruit}], [or, spend, evt]]");
		}
		{
			String q = "(cid:veg OR cid:fruit) AND (evt:spend OR evt:unset)";
			SearchQuery sq = new SearchQuery(q);
			List pt = sq.getParseTree();
			System.out.println(pt);
			assert pt.toString().equals("[and, [or, {cid=veg}, {cid=fruit}], [or, {evt=spend}, {evt=unset}]]");
		}
	}
	
	@Test
	public void testQuotedKeyVal() {
		SearchQuery sq = new SearchQuery("campaign:\"Villa Plus\"");
		List pt = sq.getParseTree();
		System.out.println(pt);
		String host = sq.getProp("campaign");
		assert host.equals("Villa Plus") : sq;
	}
	
	@Test
	public void testSimpleQuotedTerm() {
		SearchQuery sq = new SearchQuery("\"hello world\"");
		List pt = sq.getParseTree();
		System.out.println(pt);				
		assert pt.toString().equals("[\", hello world]") : pt;
		assert pt.get(1).equals("hello world");
		assert pt.get(0) == SearchQuery.KEYWORD_QUOTED;
	}
	
	@Test
	public void testSimple() {
		SearchQuery sq = new SearchQuery("host:localpub.com");
		List pt = sq.getParseTree();
		System.out.println(pt);
		String host = sq.getProp("host");
		assert host.equals("localpub.com") : sq;
	}
	

	@Test
	public void testWordsKeyVal() {
		SearchQuery sq = new SearchQuery("hello world host:localpub.com");
		List pt = sq.getParseTree();
		System.out.println(pt);
		String host = sq.getProp("host");
		assert host.equals("localpub.com") : sq;
	}
	
	@Test
	public void testBadSyntax() {
		try {
			SearchQuery sq = new SearchQuery("hello OR");
			List pt = sq.getParseTree();
			assert false;
		} catch(Exception ex) {
			// ok
		}
	}

	
	@Test
	public void testKeyValKeyVal() {
		SearchQuery sq = new SearchQuery("vert:cadburys host:localpub.com");
		List pt = sq.getParseTree();
		System.out.println(pt);
		String host = sq.getProp("host");
		assert host.equals("localpub.com") : sq;
	}
	
	@Test
	public void testCombineWithAND() {
		String baseq = "user:ww@trk OR user:mark@winterwell.com@email";
		String extraq = "evt:spend OR evt:spendadjust OR evt:donation";
		SearchQuery base = new SearchQuery(baseq);
		SearchQuery extra = new SearchQuery(base, extraq);
		assert extra.getRaw().equals("(user:ww@trk OR user:mark@winterwell.com@email) AND (evt:spend OR evt:spendadjust OR evt:donation)");
	}
	
//	@Test
//	public void testBrackets() {
//		String baseq = "user:wwvyfncgobrxvwqablhe@trk";
//		SearchQuery sq = new SearchQuery("blah");
//		sq.bracket()
//	}

}

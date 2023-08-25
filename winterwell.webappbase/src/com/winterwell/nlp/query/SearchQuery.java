package com.winterwell.nlp.query;

import static com.winterwell.utils.containers.Containers.last;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import org.eclipse.jetty.util.ajax.JSON;

import com.winterwell.utils.Mutable;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.IHasJson;
import com.winterwell.utils.web.SimpleJson;

/**
 * Interpret a search string, handling stuff like "quoted terms", OR,
 * near:London. Objects are mostly immutable.
 * <p>
 * TODO should we add substring searching? E.g. *?
 * 
 * @author daniel, Alex
 * @testedby SearchQueryTest
 */
public class SearchQuery implements Serializable, IHasJson {

	public static final class SearchFormatException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		/**
		 * 
		 * @param msg NB: sometimes repeats a (fragment of) raw
		 * @param raw Always provide the full raw string, as the error may have been
		 *            earlier
		 */
		public SearchFormatException(String msg, String raw) {
			super(msg + " in " + raw);
		}

		public SearchFormatException(String raw, Throwable cause) {
			super("raw: " + raw, cause);
		}
	}

	public static final String KEYWORD_AND = "and";

	public static final String KEYWORD_NOT = "-";

	public static final String KEYWORD_OR = "or";

	public static final String KEYWORD_QUOTED = "\"";
	
	final String LOGTAG = "SearchQuery";

	/**
	 * near:London or in:London TODO handle in:X properly. Handles one-word
	 * locations, or one-word, country (e.g. near:Boston, UK), or quoted (e.g.
	 * near:"Boston UK").
	 * 
	 */
	static final Pattern NEAR = Pattern.compile("\\b(near|in): ?(\"[^\"]+\"|\\S+, \\S{2,12}\\b|\\S+)");

	private static final long serialVersionUID = 1L;

	/**
	 * @param input
	 * @return true if input is a real word, and not search-syntax like "AND" or a
	 *         complex term
	 */
	public static boolean isAWord(Object input) {
		if (!(input instanceof String)) {
			return false;
		}
		if (input.equals(KEYWORD_AND) || input.equals(KEYWORD_NOT) || input.equals(KEYWORD_OR) || input.equals("(")
				|| input.equals("") || input.equals(")")) {
			return false;
		}
		return true;
	}

	/**
	 * isolated punctuation, If someone searches for "a b ?" we don't want only
	 * isolated instances of "?"
	 */
	private static final List<String> isolatedPunctuation = Arrays.asList("?", "!", "!?", "?!");

	/**
	 * Nested lists of keywords / words
	 */
	private transient List parseTree;

	/**
	 * ?? make final? or will that upset deserialisation? 
	 */
	private String raw;

	/**
	 * How does this class react to bad syntax? If true, it attempts detects it and
	 * throws a SearchFormatException, if false, it just tries to process it.
	 */
	private boolean strict = true;

	/**
	 * just for deserialisation. Use {@link #SearchQuery(String)} instead.
	 */
	private SearchQuery() {
		parse();
	}

	/**
	 * 
	 * @param raw
	 * @param services
	 */
	public SearchQuery(String raw) {
		assert raw != null;
		this.raw = raw.trim();
		parse();
	}

	/**
	 * @deprecated Let's refactor this to be more explicitly AND combine with AND
	 * @param base  can be null
	 * @param extra
	 */
	public SearchQuery(SearchQuery base, String extra) {
		// e.g. base: foo OR bar, extra: wibble -> (foo or bar) and (wibble)
		this(base == null ? extra : bracket(base.getRaw()) + " AND " + bracket(extra));
		assert extra != null;
	}

	/**
	 * Add ()s if not already there
	 * 
	 * @param s
	 * @return
	 */
	private static String bracket(String s) {
		if (s.startsWith("("))
			return s;
		return "(" + s + ")";
	}

	/**
	 * @deprecated - probably worth a rewrite
	 * 
	 *             Disjunctive normal form, which is what we need for pulling data
	 *             from twitter.
	 * @return a top-level OR containing only ANDs. If there is only one AND, then
	 *         the top-level single-item OR is skipped (which is dubious for a
	 *         normalised form, but hard to undo now due to saved state in SoDash).
	 * @throws if there's a NOT
	 */
	public List getNormalisedParseTree() {
		List pTree = getParseTree();
		List normed = getNormalisedParseTree2(pTree);
		return normed;
	}

	private ArrayList getNormalisedParseTree2(List node) {
		ArrayList normed = new ArrayList(node.size());
		String type = nodeType(node);
		List<List> ors = new ArrayList();
		for (int i = 0, n = node.size(); i < n; i++) {
			Object e = node.get(i);
			if (getNormalisedParseTree3_isNormalisedLeaf(e)) {
				normed.add(e);
				continue;
			}
			// recurse...
			ArrayList ne = getNormalisedParseTree2((List) e);
			// same type?
			String type2 = nodeType(ne);
			if (type.equals(type2)) {
				ne.remove(0);
				normed.addAll(ne);
				continue;
			}
			// ah -- we have and into or or vice-versa,
			// or a non-atomic not e.g. -(a OR b)
			if (type2 == KEYWORD_NOT) {
				// TODO Apply de-morgans: e.g. -(a OR b) <=> (-a AND -b)
				// ad possibly --a <=> a
				throw new TodoException(node);
			}
			if (type == KEYWORD_AND && type2 == KEYWORD_OR) {
				// change this into an OR once we've pulled up all else
				ors.add(ne);
				continue;
			}
			if (type == KEYWORD_OR && type2 == KEYWORD_AND) {
				// OR at the top -- this is the correct normal form
				normed.add(ne);
				continue;
			}
			assert false : ne;
		}

		// Hack: is it [[" term]]?
		if (normed.size() == 1 && normed.get(0) instanceof List) {
			Object op = ((List) normed.get(0)).get(0);
			if (KEYWORD_QUOTED == op) {
				// then pop the wrapping []s
				return (ArrayList) normed.get(0);
			}
		}
		// No ors?
		if (ors.isEmpty())
			return normed;

		// cross product the ors
		ArrayList<List> scenarios = new ArrayList();
		ArrayList blank = new ArrayList();
		blank.add(KEYWORD_AND);
		scenarios.add(blank);
		for (List or : ors) {
			ArrayList scenarios2 = new ArrayList();
			for (List s : scenarios) {
				// skip the leading OR
				for (int i = 1, n = or.size(); i < n; i++) {
					Object option = or.get(i);
					ArrayList s2 = new ArrayList(s);
					s2.add(option);
					scenarios2.add(s2);
				}
			}
			scenarios = scenarios2;
		}
		// add in everything else to each scenario (skipping the leading AND)
		for (int i = 1, n = normed.size(); i < n; i++) {
			Object term = normed.get(i);
			for (List s : scenarios) {
				s.add(term);
			}
		}
		ArrayList orNorm = new ArrayList();
		orNorm.add(KEYWORD_OR);
		for (List s : scenarios) {
			orNorm.add(s);
		}
		return orNorm;
		// what would be a nice algorithm for rewriting this into normal form??
		// The basic rules are:
		// (OR a1 a2) AND B => (a1 B) OR (a2 B) // pull up ORs
		// TODO - at present we can just fail for -s
		// -(a1 AND a2) => -a1 OR -a2
		// -(a1 OR a2) => -a1 AND -a2
	}

	private boolean getNormalisedParseTree3_isNormalisedLeaf(Object e) {
		if (e instanceof String)
			return true;
		List le = (List) e;
		// [- x] and [" x] are OK as leaves
		if (le.size() > 2) {
			return false;
		}
		Object head = le.get(0);
		if (head.equals(KEYWORD_NOT) || head.equals(KEYWORD_QUOTED))
			return true;
		// an error??
		return false;
	}

	/**
	 * @return the parse tree for this search. Uses nested Lists, where the first
	 *         term in each list is one of KEYWORD_AND/OR/NOT/QUOTED.
	 */
	public List getParseTree() {
		if (parseTree == null) {
			parse();
		}
		return parseTree;
	}

	/**
	 * @return the raw search query. Defines the search _apart from_ services and
	 *         types
	 */
	public String getRaw() {
		return raw;
	}

	private boolean isOR(List open) {
		return open.get(0) == KEYWORD_OR;
	}

	/**
	 * WARNING does not handle shortened urls!! E.g. Suppose http://bit.ly/abc is a
	 * youtube link -- the search "youtube" will not match it.
	 * 
	 * @param text Case is ignored
	 * @return true if this search term (ignoring service, language and location)
	 *         matches this text. I.e. the search term is contained in the text.
	 * 
	 * @testedby SearchSpecTest#testMatches()}
	 */
	public boolean matches(String text) {
		if (canonicalise) {
			// NB: this will strip punctuation from the text
			text = StrUtils.toCanonical(text.toLowerCase());
		}
		getParseTree();
		// TODO should we remove punctuation here? But what about smilies?
		boolean itMatches = matches2(text, parseTree);
		return itMatches;
	}

	private boolean matches2(String text, Object condition) {
		// NB: \\W used below is vulnerable to non-ascii. We partly defend against that
		// using StrUtils.normalise() higher up.

		// base case: a keyword
		if (condition instanceof String) {
			String term = (String) condition;
			// Match from beginning of words only?
			// e.g. "at" should match "#at" but not match "cat"
			// BUT: "bar" should match "http://foobar.com" or "foo-bar"
			// if term is a stop word, pick it up as word only, if it's not, all
			// words count
			String pString = "";
			boolean literal = false;
			// Used for awkward, regexy strings
			if (isolatedPunctuation.contains(term)) {
				pString = term;
				literal = true;
			} else if (getStopWords().contains(term)) {
				// stop words must be exact
				// BUT the current English stopword list includes some stemmed words
				// -- which don't match properly. E.g. if term="onlin", this IS counted
				// as a stopword.
				pString = "(^|\\W)" + Pattern.quote(term) + "\\b";
			} else if (term.length() < 4 && StrUtils.isWord(term)) {
				// support abbreviations, e.g. U.K.
				StringBuilder ps = new StringBuilder("(^|\\W)(");
				ps.append(term);
				ps.append("|");
				for (int i = 0, n = term.length(); i < n; i++) {
					ps.append(term.charAt(i));
					ps.append("\\.?");
				}
				ps.append(")");
				pString = ps.toString();
			} else { // normal
				pString = "(^|\\W)" + Pattern.quote(term);
			}
			Pattern pattern;
			if (literal)
				pattern = Pattern.compile(pString, Pattern.LITERAL);
			else
				pattern = Pattern.compile(pString);
			Matcher matcher = pattern.matcher(text);
			boolean mf = matcher.find();
			return mf;
		}
		List condition2 = (List) condition;
		if (condition2.isEmpty())
			return true;

		// a quoted condition?
		if (KEYWORD_QUOTED == condition2.get(0)) {
			assert condition2.size() == 2 : condition2;
			String term = (String) condition2.get(1);
			// Allow inter-word punctuation IF the search term does not specify punctuation.
			// e.g. "inter word" should match "inter-word", TODO should "UK" match "U.K."??
			String[] words = term.split(" ");
			StringBuilder pString = new StringBuilder("(^|\\W|_)");
			for (String word : words) {
				pString.append(Pattern.quote(word));
				pString.append("\\W+");
			}
			StrUtils.pop(pString, 3);
			// Complete word match: Block e.g. "bar" matching "barry" -- but allow plurals
			// -s, -es, -ies as a special case
			// Why do we have -ies? If it is for eg baby -> babies then we have to allow
			// drop-the-y
			pString.append("(ies|es|s)?($|\\W|[_0-9])");
			Pattern pattern = Pattern.compile(pString.toString());
			Matcher matcher = pattern.matcher(text);
			boolean mf = matcher.find();
			if (mf) { // for debug
				String matched = matcher.group();
				String m2 = StrUtils.substring(text, matcher.start() - 2, matcher.end() + 2);
				System.out.println(matched);
			}
			return mf;
		}

		// a NOT condition?
		if (KEYWORD_NOT == condition2.get(0)) {
			assert condition2.size() == 2 : condition2;
			boolean subOk = matches2(text, condition2.get(1));
			return !subOk;
		}

		// an OR condition?
		if (KEYWORD_OR == condition2.get(0)) {
			for (int i = 1; i < condition2.size(); i++) {
				boolean ok = matches2(text, condition2.get(i));
				if (ok)
					return true;
			}
			return false;
		}

		// an and condition
		assert KEYWORD_AND == condition2.get(0) : condition2;
		for (int i = 1; i < condition2.size(); i++) {
			Object ci = condition2.get(i);
			boolean ok = matches2(text, ci);
			if (!ok)
				return false;
		}
		return true;
	}

	Collection<String> _stopwords = new HashSet<>();

	private boolean canonicalise;

	private Collection<String> getStopWords() {
		// TODO Auto-generated method stub
		return _stopwords;
	}

	private String nodeType(List node) {
		String t = (String) node.get(0);
		assert t == KEYWORD_AND || t == KEYWORD_NOT || t == KEYWORD_OR || t == KEYWORD_QUOTED : t;
		return t;
	}

	// Note: behaviour may be weird if searchTerm != this.searchTerm (though it's
	// handy for some tests)
	List parse() {
		if (parseTree != null)
			return parseTree;
		String searchTerm = raw;
		// NB: canonicalise at the top-level would lose brackets. So do it at the leaf
		// level
		List output = new ArrayList();
		output.add(KEYWORD_AND);
		ArrayList<List> stack = new ArrayList();
		stack.add(output);
		// Parse!
		try {
			parse2(searchTerm, 0, stack, output);
			// drop the leading AND if it's not needed
			if (output.size() == 2 && output.get(0) == KEYWORD_AND && output.get(1) instanceof List) {
				output = (List) output.get(1);
			}
			// convert key:value
			parse2_keyvalue(output);
			this.parseTree = output;
			return output;
		} catch (SearchFormatException ex) {
			throw ex;
		} catch (Throwable ex) {
			// for debug: add in the causing string
			throw new SearchFormatException(raw, ex);
		}
	}

	/**
	 * key must start with a letter, but it can then also use 0-9 and . 
	 */
	static final Pattern kv = Pattern.compile("([a-zA-Z][a-zA-Z0-9\\\\.]*|@id):(.+)");

	/**
	 * Modify output - Convert "k:v" into {k:v}. Filters nulls. Values CANNOT
	 * contain spaces. Does NOT support quoting :(
	 * 
	 * @param output
	 */
	private void parse2_keyvalue(List output) {
		for (int i = 0; i < output.size(); i++) {
			Object bit = output.get(i);
			// recurse?
			if (bit instanceof List) {
				List lbit = (List) bit;
				parse2_keyvalue(lbit);
				// pop redundant and?
				if (lbit.size()==2 && KEYWORD_AND.equals(lbit.get(0))) {
					output.set(i, lbit.get(1));
				}
				continue;
			}
			// k:v?
			String sbit = (String) bit;
			if ( ! sbit.endsWith(":")) {
				continue;
			}
//			Matcher m = kv.matcher(sbit);
//			if (!m.matches())
//				continue;
			// avoid breaking up urls, which will also match k:v
			if (sbit.contains("http")) {
				//m.group(1).startsWith("http") || m.group(2).startsWith("//")) {
				continue;
			}
			String k = sbit.substring(0, sbit.length()-1); // m.group(1);
			if (k.startsWith(".") || k.endsWith(".")) {
				continue; // paranoia
			}			
			if (i+1 == output.size()) {
				continue; // no bvalue - maybe malformed
			}
			Object v = output.get(i+1); // m.group(2);
			// skip "null" or "undefined" which suggest errors. "unset" is the correct (safe) format for no-value-set.
			if ("null".equals(v) || "undefined".equals(v)) {
				output.remove(i);
				output.remove(i);
				i--;
				continue;
			}
			Object v2 = v;
			String vs = v instanceof String? (String) v : "";
			// HACK 2-level k:v:v e.g. "due:before:today"
			if (vs.endsWith(":") && (i+2)<output.size()) {
				Object v2b = output.get(i+2);
				v2 = Collections.singletonMap(vs.substring(0, vs.length()-1), v2b);
				output.remove(i+2);
			}
//			Matcher m2 = kv.matcher(v.toString());
//			if (m2.matches()) {
//				v2 = Collections.singletonMap(m2.group(1), m2.group(2));
//			}
			// done
			Map<String, Object> keyValue = Collections.singletonMap(k, v2);
			output.set(i, keyValue);
			output.remove(i+1);
		}
	}

	void parse2(String searchTerm, int index, List<List> stack, List parse) {
		// read words
		Mutable.Int i = new Mutable.Int(index);
		String word = null, prevTerm = null;
		while (i.value < searchTerm.length()) {
			prevTerm = word;
			int startI = i.value;
			word = parse3_nextWord(searchTerm, i);
			if (word.isEmpty())
				continue;
			List open = stack.get(stack.size() - 1);
			if (KEYWORD_OR.equalsIgnoreCase(word)) {
				parse3_or(stack, open, word);
				continue;
			}
			if (KEYWORD_AND.equalsIgnoreCase(word)) {
				continue; // ignore - its the default
			}
			if (KEYWORD_NOT.equalsIgnoreCase(word)) {
				parse3_not(stack, open, i.value, prevTerm);
				continue;
			}
			// open a bracket?
			if ("(".equals(word)) {
				ArrayList newOpen = new ArrayList();
				newOpen.add(KEYWORD_AND); // assume and
				open.add(newOpen);
				stack.add(newOpen);
				continue;
			}
			// close a bracket?
			if (")".equals(word)) {
				if (strict && stack.size() == 1) {
					throw new SearchFormatException(
							"Bad Syntax: improper \")\" nesting at index:" + (i.value - 1) + " in " + searchTerm, raw);
				}
				// or be generous around bad syntax
				open = parse4_close(stack);
				// close a not?
				parse3_closeNot(stack);
				continue;
			}

			// New AND group forms in the case of "a OR b c" => [OR a [b c]]
			// Identified by:
			// -- we're in an OR group which already has its first 2+ terms (e.g. open = [or
			// a b])
			// -- This term is a word and the _previous term_ was not an OR
			if (open.size() > 2 && open.get(0).equals(KEYWORD_OR) 
					&& ! KEYWORD_OR.equalsIgnoreCase(prevTerm) 
					&& ! prevTerm.endsWith(":")) 
			{
				List oldOpen = open;
				open = new ArrayList();
				open.add(KEYWORD_AND);
				Object prevWord = oldOpen.remove(2);
				open.add(prevWord);
				oldOpen.add(open);
				stack.add(open);
			}

			// Just a normal keyword!
			// - was it quoted? (different match behaviour)
			// Note: i>1 since we've pulled a word off
			if (canonicalise) {
				word = StrUtils.toCanonical(word);
			}
			if (searchTerm.charAt(startI) == '"') {
				// NB: canonicalise could mess with desired "" matching behaviour.
				// However since we're using canonicalise on the input in matches(), we better
				// apply
				// it here too.
				open.add(Arrays.asList(KEYWORD_QUOTED, word));
			} else {
				open.add(word);
			}
			// close a not?
			parse3_closeNot(stack);
		}

		// still an open or?
		if (strict && !stack.isEmpty()) {
			List open = last(stack);
			if (open.size() == 2 && open.get(0) == KEYWORD_OR) {
				throw new SearchFormatException("Unclosed OR", searchTerm);
			}
		}
	}

	private void parse3_closeNot(List<List> stack) {
		if (stack.size() < 2)
			return;
		// NOT group -- only takes one element, so close once it's filled
		if (stack.get(stack.size() - 1).get(0).equals(KEYWORD_NOT)) {
			List not = stack.remove(stack.size() - 1);
			assert not.size() == 2 : not + " untimely removed from " + stack;
		}
	}

	/**
	 * Read the next word, advancing the index
	 * 
	 * @param searchTerm
	 * @param index
	 * @return next token (can be empty, though index must always advance)
	 */
	String parse3_nextWord(String searchTerm, Mutable.Int index) {
		// NB: ":" is not a special character, so name:alice and name:"Alice Smith"
		// parse as one word.
		// though name: Alice doesn't work.
		// Arguably we should change that.
		boolean escaped = false;
		boolean quoted = false;
		StringBuilder word = new StringBuilder();
		while (index.value < searchTerm.length()) {
			char c = searchTerm.charAt(index.value);
			index.value++;
			if (escaped) {
				word.append(c);
				escaped = false;
				continue;
			}
			// Special opening chars: ()s and - for NOT
			if (!quoted && word.length() == 0) {
				// Don't allow leading " "s, this gets rid of empty words, and
				// solves several problems
				if (Character.isWhitespace(c))
					continue;

				if (c == '(') {
					return "(";
				}
				if (c == ')')
					return ")";
				if (c == '-')
					return KEYWORD_NOT;
//				// (some) Smilies TODO
//				if (c == ':' || c == ';') {
//					String smilie = EmoticonDictionary.getDefault().match(searchTerm, index.value - 1);
//					if (smilie!=null) {
//						index.value = index.value + (smilie.length() - 1);
//						return smilie;
//					}
//				}
			}
			// escape next char?
			if (c == '\\') {
				escaped = true;
				continue;
			}
			if (c == '"') {
				if (quoted) {
					// end of quoted word
					return word.toString();
				}
				quoted = true;
				continue;
			}
			// special case: brackets
			if (c == ')' || c == '(') {
				index.value--; // don't consume - return next char
				return word.toString();
			}

			// end of non-quoted word?
			if (!quoted && Character.isWhitespace(c)) {
				return word.toString();
			}
			word.append(c);
			// end of key:
			if (!quoted && c==':') {
				return word.toString();
			}
		}
		if (strict) {
			if (quoted) {
				throw new SearchFormatException("Bad syntax, incorrect quoting nesting " + searchTerm, raw);
			}
		}
		return word.toString();
	}

	private void parse3_not(List<List> stack, List open, int i, String prevTerm) {
		// Kill double or loner NOT commands if strict
		if (strict) {
			if (i >= raw.length()) {
				// Log it instead of throwing exception
				Log.d(LOGTAG, "Bad Syntax: Final NOT at index:" + i + raw);
				return;
//				throw new SearchFormatException("Bad Syntax: Final NOT at index:" + i, raw);
			}
			char next = raw.charAt(i);
			if (next == ' ') { // Ignore " - " in string
				return;
			}
			if ((next == '-') || (next == ')') || next == ' ') {
				throw new SearchFormatException("Bad Syntax: Floating, multiple or Final NOT at index:" + i, raw);
			}
		}

		// Are we in an OR group? If so close it -- unless there's an explicit or to
		// bring this not inside
		if (open.size() != 0 && (KEYWORD_OR.equalsIgnoreCase(open.get(0).toString()))
				&& !KEYWORD_OR.equalsIgnoreCase(prevTerm)) {
			open = parse4_close(stack);
		}

		// If it's an AND group, we just want to start a new not group in it
		ArrayList not = new ArrayList();
		not.add(KEYWORD_NOT);
		open.add(not);
		stack.add(not);
//		} else {
//			// an empty group -- make it a not group
//			open.add(KEYWORD_NOT);
//		}
	}

	/**
	 * 
	 * @param stack
	 * @param open modify this to be OR (if needed)
	 * @param word
	 */
	private void parse3_or(List<List> stack, List open, String word) {
		// ignore or flag bad syntax on inital ORs
		if (open.size() < 2) {
			if (strict) {
				throw new SearchFormatException("Bad Syntax: initial OR", raw);
			}
			return;
		}

		// chain of ORs?
		if (isOR(open)) {
			return;
		}
//		// AND within a chain of ORs? This causes more problems than it's worth 
//		if (isAND(open) && stack.size()>1 && isOR(stack.get(stack.size()-2))) {
//			List noLongerOpen = stack.remove(stack.size() - 1);
//			return;
//		}

		// Convert the open group into an OR group
		// binding: a b OR C => [[a b] OR c]]
		// Get a clone of current open group, this is to handle " a b OR c d"
		Object a;

		// pick out the first part of the "a OR b" -- which may be a list
		// ...simplify [AND a]
		if (open.size() == 2 && open.get(0).equals(KEYWORD_AND)) {
			a = open.get(1);
			// } else if (open.size() == 1) {
			// a = open.get(0);
		} else {
			a = new ArrayList(open);
		}
		open.clear();
		open.add(KEYWORD_OR);
		open.add(a);
	}

	private List parse4_close(List<List> stack) {
		if (stack.isEmpty()) {
			if (strict) {
				throw new SearchFormatException("Unbalanced stack", raw);
			}
			return null; // can this happen??
		}
		// remove the open element
		List noLongerOpen = stack.remove(stack.size() - 1);
		if (stack.isEmpty()) {
			// stack surgery: if we had OR as the top-level term,
			// we may now need to embed it in an AND
			List topLevel = noLongerOpen; // keep the output object the same
			// clone the old
			ArrayList oldTopLevel = new ArrayList(noLongerOpen);
			topLevel.clear();
			topLevel.add(KEYWORD_AND);
			topLevel.add(oldTopLevel);
			stack.add(topLevel);
		}
		return stack.get(stack.size() - 1);
	}

	public SearchQuery setStrict(boolean b) {
		this.strict = b;
		return this;
	}

	public boolean isStrict() {
		return strict;
	}

	@Override
	public String toString() {
		return "SearchQuery[" + raw + "]";
	}

	@Override
	public void appendJson(StringBuilder sb) {
		sb.append(toJSONString());
	}

	@Override
	public String toJSONString() {
		return new SimpleJson().toJson(toJson2());
	}

	@Override
	public Object toJson2() throws UnsupportedOperationException {
		return getRaw();
	}

	/**
	 * Convenience method. IF propName occurs at the top-level, then return the
	 * value. WARNING: key:val props can occur lower-down, e.g. in and/or/not
	 * clauses, and this method does NOT handle that.
	 * 
	 * @param {*} propName
	 */
	public String getProp(String propName) {
		List pt = this.getParseTree();
		Map prop = (Map) Containers.first(pt, bit -> bit instanceof Map && ((Map) bit).containsKey(propName));
		// What to return if prop:value is present but its complex??
		if (prop==null) return null;
		Object v = prop.get(propName);
		if (v instanceof String) return (String) v;
		if (v instanceof List) {
			List vl = (List) v;
			// quoted?
			if ( ! KEYWORD_QUOTED.equals(vl.get(0))) {
				throw new TodoException(v);
			}
			Object vs = vl.get(1);
			return (String) vs;
		}
		throw new TodoException(v.getClass()+" "+v);
	}

	public SearchQuery setCanonicaliseText(boolean b) {
		parseTree = null;
		canonicalise = b;
		return this;
	}

	/**
	 * AND a top-level OR over a set of prop:values. Convenience for a common enough case.
	 * @param propKey
	 * @param propValues This will be de-duped. Must NOT contain null or blank
	 * @return a new SearchQuery
	 */
	public SearchQuery withPropOr(String propKey, Collection<String> propValues) {
		if (propValues.isEmpty()) {
			throw new IllegalArgumentException(propKey+":nothing");
		}
		// start with "key:"
		StringBuilder cs = new StringBuilder(propKey+":");
		HashSet noDupes = new HashSet(); // NB: simple running propValues into a HashSet would scramble the order. Which is probably OK but not expected or stable over repeated calls.
		for (String propValue : propValues) {
			if (propValue.isBlank()) {
				throw new IllegalArgumentException("blank in "+propValues);
			}
			if (noDupes.contains(propValue)) {
				continue; // de-dupe
			}
			noDupes.add(propValue);
			// escape value with quotes?
			if (propValue.contains(" ") || propValue.contains(":")) { // what characters need escaping??
				cs.append('"');cs.append(propValue);cs.append('"');
			} else {
				cs.append(propValue);	
			}			
			cs.append(" OR ");
			cs.append(propKey);cs.append(":");
		}
		StrUtils.pop(cs, propKey.length()+5);
		if (raw.isEmpty()) {
			// this is the whole query - done
			return new SearchQuery(cs.toString());
		}
		// combine with existing query
		String and = " AND ";
		// HACK: assumed and?
		if (KEYWORD_AND.equals(this.parseTree.get(0)) && ! raw.contains(" AND ") && ! raw.contains(" and ")) {
			and = " ";
		}
		// Only bracket if needed
		if (propValues.size()==1) {
			return new SearchQuery(raw+and+cs);
		}
		return new SearchQuery(raw+and+"("+cs+")");
	}

	/**
	 * return a new SearchQuery with key:value
	 * @param key
	 * @param value
	 * @return use this!
	 */
	public SearchQuery withProp(String key, String value) {
		return withPropOr(key, Collections.singleton(value));
	}

}

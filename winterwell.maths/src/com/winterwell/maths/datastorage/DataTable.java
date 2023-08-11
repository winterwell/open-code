package com.winterwell.maths.datastorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.winterwell.utils.Key;
import com.winterwell.utils.Printer;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.Table;
import com.winterwell.utils.io.CSVReader;
import com.winterwell.utils.io.CSVWriter;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.io.ISerialize;
import com.winterwell.utils.log.KErrorPolicy;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.IHasHtml;
import com.winterwell.utils.web.IHasJson;
import com.winterwell.web.HtmlTable;

/**
 * A simple data table, for when a List or HashMap isn't quite good enough. 
 * Basically a List of Object[].
 * Has an index on the first column. It is designed to be close to a drop-in
 * replacement for HashMap and List.
 * 
 * Probably thread safe.
 * 
 * @author daniel
 * @param <C1>
 *            type of the 1st column, usually String
 * @testedby  DataTableTest
 */
public class DataTable<C1> extends Table<C1, Object[]> implements IHasHtml, IHasJson {
	static KErrorPolicy exceptionPolicy = KErrorPolicy.THROW_EXCEPTION;


	public void sort(int column) {
		Comparator<Object[]> comp = (r1,r2) -> {
			Object a = Containers.get(r1, column);
			Object b = Containers.get(r2, column);
			return Utils.compare(a, b);
		};
		rows.sort(comp);
	}
	
	private static final long serialVersionUID = 1L;

	/**
	 * Hokey method to make it slightly nicer to write data-processing
	 * "scripts". Since it's shared across all DataTables, avoid like the plague
	 * in real code.
	 * 
	 * @param policy
	 */
	@Deprecated
	public static void setExceptionPolicy(KErrorPolicy policy) {
		exceptionPolicy = policy;
	}

	private final ISerialize[] rowSerialisers;

	@Override
	protected final C1 getRowKey(Object[] row) {
		return (C1) row[0];
	}
	
	
	@Override
	public final Object[] getRow(int row) {
		// NB this override is to give type even if C1 is not set
		return super.getRow(row);
	}
	

	/**
	 * Convenience for working with List instead of Object[]
	 * @param row
	 * @return
	 */
	public List<Object> getRowList(int row) {
		return Arrays.asList(getRow(row));
	}	

	
	/**
	 * Loads the csv file, if given.
	 * 
	 * @param in
	 *            Can be null. Will be closed after reading.
	 * @param rowSerialisers
	 *            Can be null. Can contain nulls (which will result in the
	 *            Strings being used as-is). Can be shorter than the full row
	 *            length.
	 */
	public DataTable(CSVReader in, ISerialize... rowSerialisers) {
		super(Object[].class);
		this.rowSerialisers = rowSerialisers;
		if (in == null)
			return;
		load(in);
		FileUtils.close(in);
	}

	/**
	 * Create an empty DataTable with no serialisers.
	 */
	public DataTable() {
		super(Object[].class);
		this.rowSerialisers = null;
	}
	
	public DataTable(ISerialize... rowSerialisers) {
		this(Collections.EMPTY_LIST, rowSerialisers);
	}

	/**
	 * 
	 * @param rows Object[] or List
	 * @param rowSerialisers
	 */
	public DataTable(List rows, ISerialize... rowSerialisers) {
		super(Object[].class);
		this.rowSerialisers = rowSerialisers;
		for (Object row : rows) {
			List<Object> rowAsList = Containers.asList(row);
			if (rowAsList.isEmpty()) {
				continue; // NB: Xero pads its csv style output with []s
			}
			add(rowAsList);
		}
	}

	/**
	 * Add a row. The 0th item will be used as the index for
	 * {@link #get(Object)}.
	 * 
	 * @param row
	 *            All rows must be the same length.
	 */
	public synchronized void add(Object... row) {
		// Guard against an easy mistake (which could technically be legit, but
		// probably is a bug)
		if (row.length == 1 && row[0] != null) {
			if (row[0].getClass().isArray()) {
				Log.w("data", "converting " + Printer.toString(row[0]) + " to Object[]");
				Object innards = row[0];
				List<Object> list = Containers.asList(innards);
				row = list.toArray();
			} else if (row[0] instanceof List) {
				row = ((List)row[0]).toArray();
			}
		}
		super.add(row);
	}

	public void add(List row) {
		add(row.toArray());
	}
	
	public void addColumn(Collection col) {
		if (col.size() != size()) {
			// hm! a short / long column
			Log.v("DataTable", "Hm: column size mismatch: table:"+size()+" col: "+col.size());
		}
		int w = getWidth();
		int wPlus = w+1;
		int i=0;
		for (Object vi : col) {
			Object[] row2;
			if (i < size()) {
				row2 = Arrays.copyOf(getLowLevel().get(i), wPlus);
				getLowLevel().set(i, row2);
			} else {
				row2 = new Object[wPlus];
				add(row2);
			}
			row2[w] = vi;			
			i++;
		}
	}

	/**
	 * 
	 * @param i
	 * @return A fresh object - edits to the List will not alter this DataTable 
	 */
	public List getColumn(int i) {
		ArrayList col = new ArrayList(size());
		for (Object[] row : this) {
			col.add(row[i]);
		}
		return col;
	}

	public void removeColumn(int i) {		
		for(int r=0; r<size(); r++) {
			Object[] row = getRow(r);
			Object[] row2 = Arrays.copyOf(row, row.length-1);
			for(int j=i; j<row.length; j++) {
				row2[j] = row[j+1];
			}
			throw new TodoException();
		}
	}

	/**
	 * Status: sketch!
	 * 
	 * Uses the {@link #rowSerialisers} -- provided they're Keys or AFields --
	 * to create a map-view of the row.
	 * 
	 * @param row
	 */
	public Map getRowMap(int row) {
		Object[] arr = getRow(row);
		ArrayMap map = new ArrayMap();
		for (int i = 0; i < rowSerialisers.length; i++) {
			ISerialize ser = rowSerialisers[i];
			if (ser instanceof Key) {
				String name = ((Key) ser).getName();
				map.put(name, arr[i]);
			} else {
				// skip
			}
		}
		return map;
	}

	/**
	 * 
	 * @return the number of columns in this table. This is a convenience for getting the length of the 1st row.
	 * 0 if there are no rows.
	 */
	public int getWidth() {
		return size()==0? 0 : getRow(0).length;
	}


	public void load(CSVReader in) {
		for (String[] row : in) {
			if (row.length == 0) {
				continue;
			}
			Object[] row2 = new Object[row.length];
			// convert?
			for (int i = 0; i < row.length; i++) {
				// See note in save
				if (row[i].equals("")) {
					row2[i] = null;
					continue;
				}
				try {
					ISerialize rs = rowSerialisers != null
							&& rowSerialisers.length > i ? rowSerialisers[i]
							: null;
					Object x = rs == null ? row[i] : rowSerialisers[i]
							.fromString(row[i]);
					row2[i] = x;
				} catch (Exception e) {
					switch (exceptionPolicy) {
					case THROW_EXCEPTION:
						throw Utils.runtime(e);
					case DIE: {
						Log.report("Problem in CSV load, and DIE requested. Exiting immediately.");
						System.exit(1);
					}
					case REPORT:
						Log.report(e + " from " + row[i]);
					case IGNORE:
						break;
					case RETURN_NULL:
						row2[i] = null;
						break;
					case DELETE_CAUSE:
						throw new TodoException(e.getMessage());
					}
				}
			}
			// row done
			add(row2);
		}
	}

	/**
	 * @param out
	 *            This will be closed at the end.
	 */
	public void save(CSVWriter out) {
		try {
			for (Object[] objs : this) {
				String[] strings = new String[objs.length];
				// convert?
				for (int i = 0; i < objs.length; i++) {
					// This has the effect of writing null strings for null
					// values
					if (objs[i] == null) {
						strings[i] = null;
						continue;
					}
					ISerialize rs = rowSerialisers != null
							&& rowSerialisers.length > i ? rowSerialisers[i]
							: null;
					String s = rs == null ? String.valueOf(objs[i])
							: rowSerialisers[i].toString(objs[i]);
					strings[i] = s;
				}
				out.write(strings);
			}
			out.close();
		} catch (Exception e) {
			throw Utils.runtime(e);
		}
	}

	@Override
	public void appendHtml(StringBuilder sb) {
		sb.append(toHTMLString());
	}

	@Override
	public String toHTMLString() {
		HtmlTable tbl = new HtmlTable(getWidth());
		tbl.setCSSClass("table"); // let's default to Bootstrap
		for (Object[] row : rows) {
			tbl.addRow(row);
		}
		return tbl.toHTML();
	}


	/**
	 * Assumes row 0 is String headers
	 * @param colName
	 * @return
	 */
	public int getColumnIndex(String colName) {
		Object[] hs = getRow(0);
		int i = Containers.indexOf(colName, hs);
		return i;
	}


/**
 * Merge in extra columns. Assumes the 1st row is headers, and the 1st column is labels.  
 * The merged column headers are sorted, apart from the 1st labels column which stays 1st.
 * <p> 
 * Note: We don't have a function for merging extra rows, because that is easy - this is a row-based storage (similar to a csv file), so just add them!
 * 
 * @param newData This will overwrite oldData if there is any overlap
 * @param oldData
 * @return a new merged DataTable
 */
	public static DataTable<String> merge(DataTable<String> newData, DataTable<String> oldData) {
		Object[] oldHeaders = oldData.size()==0? new Object[0] : oldData.getRow(0);
		Object[] freshHeaders = newData.size()==0? new Object[0] : newData.getRow(0);
		if (freshHeaders.length==0 && oldHeaders.length==0) {
			return oldData; // both empty, no-op
			// NB: if one is empty, we still need to sort the columns for consistent behaviour :(
		}

		// This is the index column, and should not be sorted
		Object indexColumnHeader = freshHeaders.length==0? oldHeaders[0] : freshHeaders[0]; 

		// sort the headers (dropping the index column)
		Set allHeaders = new HashSet();
		if (oldHeaders.length>0) allHeaders.addAll(Arrays.asList(oldHeaders).subList(1, oldHeaders.length));
		if (freshHeaders.length>0) allHeaders.addAll(Arrays.asList(freshHeaders).subList(1, freshHeaders.length));		
		ArrayList<String> hlist = new ArrayList(allHeaders);
		Collections.sort(hlist); //, (a,b) -> new Time(a).compareTo(new Time(b)));
		ArrayList hlist2 = new ArrayList();
		hlist2.add(indexColumnHeader);
		hlist2.addAll(hlist);

		// fill in the data
		HashMap<String,Map> colForRow = new HashMap();
		merge2(oldData, colForRow);
		// fresh data last, so it can override
		merge2(newData, colForRow);
		// copy it out - preserving order
		DataTable merged = new DataTable();
		merged.add(hlist2);
		// TODO merge row lists, perhaps using levenshtein to slot them in
		List<String> rlist = newData.getColumn(0); // HACK just the newData rows
		for(String r : rlist) {
			if (Utils.isBlank(r)) {
				continue;
			}
			Map col = colForRow.get(r);
			ArrayList row = new ArrayList();
			row.add(r);
			for(String h : hlist) {
				Object v = col.get(h);
				row.add(v);
			}
			merged.add(row);
		}
		
		// HACK tack any missing oldData rows onto the bottom
		List<String> rlist2 = oldData.getColumn(0); 
		rlist2.removeAll(rlist);
		for(String r : rlist2) {
			if (Utils.isBlank(r)) {
				continue;
			}
			Map col = colForRow.get(r);
			if (col==null) continue; // paranoia
			ArrayList row = new ArrayList();
			row.add(r);
			for(String h : hlist) {
				Object v = col.get(h);
				row.add(v);
			}
			merged.add(row);
		}
		
		return merged;
	}


	private static void merge2(DataTable<String> data, HashMap<String, Map> colForRow) {
		if (data.size()==0) {
			return;
		}
		Object[] headers = data.getRow(0);
		for(Object[] row : data) {
			String rName = (String) row[0];
			Map col = colForRow.computeIfAbsent(rName, n -> new HashMap());
			for(int i=1; i<row.length; i++) {
				Object v = row[i];
				Object h = headers[i];
				col.put(h, v);
			}		
		}
	}


	@Override
	public List<List<Object>> toJson2() throws UnsupportedOperationException {
		ArrayList rows2 = new ArrayList();
		for(Object[] row : this) {
			rows2.add(new ArrayList(Arrays.asList(row)));
		}
		return rows2;
	}


	public static DataTable fromRows(List<Object[]> rows) {
		DataTable dt = new DataTable<>();
		for (Object[] objects : rows) {
			dt.add(objects);
		}
		return dt;
	}






}

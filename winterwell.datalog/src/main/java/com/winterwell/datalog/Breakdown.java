package com.winterwell.datalog;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.time.Dt;
import com.winterwell.utils.web.WebUtils2;

/**
 * 
 * e.g. pub{"count":"sum"}
 * @author daniel
 *
 */
public final class Breakdown {

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(by);
		result = prime * result + Arrays.hashCode(fields);
		result = prime * result + Objects.hash(interval, op);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Breakdown other = (Breakdown) obj;
		return Arrays.equals(by, other.by) && Arrays.equals(fields, other.fields)
				&& Objects.equals(interval, other.interval) && op == other.op;
	}

	/**
	 * e.g. "pub" or "pub/time"
	 * Never null or empty! null is converted into [""]
	 */
	final String[] by;
	/**
	 * e.g. "count" or "price"
	 * Never null or empty. Usually a single value.
	 */
	final String[] fields;
	
	public String[] getFields() {
		return fields;
	}
	
	/**
	 * Currently assumed to be sum and ignored??
	 */
	final KBreakdownOp op;
	
	/**
	 * defaults to daily
	 */
	Dt interval;
	private String specialField;
	
	public Breakdown setInterval(Dt interval) {
		this.interval = interval;
		return this;
	}
	
	public List<String> getBy() {
		return Arrays.asList(by);
	}
	
	/**
	 * 
	 * @param by The row-key. E.g. "pub" for results by publisher, 
	 * or ",pub" for top-level + breakdown-by-pub. 
	 * Can be null
	 * NB: a trailing comma will be ignored, but a leading one works.
	 * 
	 * There are some special values: "time", "dateRange", "timeofday"
	 * See ESDataLogSearchBuilder
	 * 
	 * @param field e.g. "count" or "price" Special value: "emissions"
	 * @param operator e.g. "sum"
	 */
	public Breakdown(String by, String field, KBreakdownOp operator) {
		this(by==null? new String[]{""} : by.split("/"), new String[]{field}, operator);
	}
	
	/**
	 * HACK
	 */
	public static final String EMISSIONS = "emissions";
	/**
	 * HACK
	 */
	public static final String COUNTCO2 = "countco2";
	
	public Breakdown(String[] by, String[] fields, KBreakdownOp operator) {
		this.by = by;
		Utils.check4null(by);
		for(String b : by) assert ! b.contains(",") : this;				
		// HACK emissions is special
		if (Containers.contains(EMISSIONS, fields)) {			
			assert fields.length==1 : fields;
			specialField = fields[0];
			String[] f2s = "count co2 co2base co2creative co2supplypath".split(" ");
			fields = f2s;
		} else if (Containers.contains(COUNTCO2, fields)) {
			assert fields.length==1 : fields;
			specialField = fields[0];
			String[] f2s = "count co2".split(" ");
			fields = f2s;
		}
		this.fields =fields;
		
		this.op = operator;
		assert ! fields[0].isEmpty();
	}
	
	/**
	 * 
	 * @param bd e.g. `pub/time{"price":"sum"}`
	 */
	public static Breakdown fromString(String bd) {
		String[] breakdown_output = bd.split("\\{");
		String[] bucketBy = breakdown_output[0].trim().split("/");
		// default to sum of `count`
		if (breakdown_output.length == 1) {
			return new Breakdown(bucketBy, new String[]{"count"}, KBreakdownOp.sum);
		}
		String json = bd.substring(bd.indexOf("{"), bd.length());
		// TODO handle emissions and countco2
//			if (("{\""+EMISSIONS+"\":\"sum\"}").equals(json)) {
//				// Hack: shortcut for greendata emissions
//				json = "{\"count\":\"sum\",\"co2\":\"sum\",\"co2base\":\"sum\",\"co2creative\":\"sum\",\"co2supplypath\":\"sum\"}";
//			} else if (("{\""+COUNTCO2+"\":\"sum\"}").equals(json)) {
//				json = "{\"count\":\"sum\",\"co2\":\"sum\"}"; // Hack: emissions shortcut for performance
//			}
		Map reportSpec = WebUtils2.parseJSON(json);
		String[] _field = (String[]) reportSpec.keySet().toArray(new String[0]);
		Object _op = reportSpec.get(_field[0]);
		KBreakdownOp kop = KBreakdownOp.valueOf(_op.toString());
		return new Breakdown(bucketBy, _field, kop);
	}

	/**
	 * Suitable for passing to the DataLog /data endpoint.
	 * 
	 * NB: Does not include interval 
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(StrUtils.join(by, "/"));
		sb.append('{');
		String[] _fields = fields;
		// HACK for co2 emissions
		if (specialField!=null) {
			_fields = new String[] {specialField};
		}
		for(String f : _fields) {			
			sb.append('"');sb.append(f);sb.append('"');
			sb.append(':');
			sb.append("\""+op+"\",");
		}
		StrUtils.pop(sb, 1);
		sb.append('}');
		return sb.toString();
	}

	public String getField() {
		if (fields.length != 1) {
			throw new IllegalStateException("Single field requested but: "+Printer.str(fields));
		}
		return fields[0];
	}
}

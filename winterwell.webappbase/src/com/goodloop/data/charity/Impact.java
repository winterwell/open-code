package com.goodloop.data.charity;

import java.util.Objects;

import com.goodloop.data.MediaObject;
import com.goodloop.data.Money;
import com.goodloop.data.Quotation;
import com.goodloop.data.VideoObject;
import com.goodloop.portal.KImpactName;
import com.winterwell.data.KStatus;
import com.winterwell.es.ESKeyword;
import com.winterwell.es.ESNoIndex;
import com.winterwell.jgeoplanet.SimplePlace;
import com.winterwell.utils.Utils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;

/**
 * What Impact did we have?
 * Also: What Impact will we have (dynamic rates)?
 * 
 * NB: This is a successor to SoGive's Output.java class
 * @author daniel
 *
 */
public class Impact {
	
	public boolean isDynamic() {
		if (dynamic != null) {
			return dynamic;
		}
		if (rate!=null) {
			Log.d("Impact", "old data - dynamic rate but not explicitly set as dynamic");
			return true;
		}
		return false;
	}
	
	public void setDynamic(Boolean dynamic) {
		this.dynamic = dynamic;
	}
	
	/**
	 * charity category -- similar to Charity Navigator's 11 categories See NGO.js
	 * NGO.CATEGORY
	 */
	@ESKeyword
	String category;
	

	@ESKeyword
	String subcategory;

	/**
	 * UN Sustainable Development Goal (SDG)
	 */
	Integer unsdg;

	/**
	 * Charity/NGO ID - the provider of the Impact.
	 * e.g. Gold Standard for carbon offsets, Eden Reforestation for trees
	 */
	// ??could we change to `cid` for consistency elsewhere??
	@ESKeyword
	String charity;
	
	/**
	 * Flex: Allow local override of logo or text by using a local NGO object here.
	 * NB: NGO.java is not visible in this package.
	 */
	@ESNoIndex
	Object localCharity;
	
	/**
	 * When was this calculation for?
	 */
	Time date;
	
	/**
	 * e.g. "tree(s)" This is a "canonical" name that should be managed, so we can match and add impacts.
	 * @see KImpactName
	 */	
	@ESKeyword
	String name;
	
	/**
	 * native currency
	 */
	Money amount;
	
	/**
	 * What should this campaign raise? Normally fixed by the budget
	 */
	Money amountTarget;
	
	/**
	 * {@link #amountUSD} is preferred -- But the company is UK registered and our accountancy is mostly in £s.
	 * So lets store the GBP amount.
	 */
	public Double amountGBP;
	
	/** For easy summation and long-term FX handling, 
	 * store amount in dollars (which are considered a stable currency) */
	public Double amountUSD;
	
	/**
	 * How many units of this impact ({@link #name}) have been committed/bought?
	 */
	public Double n;
	
	/**
	 * Optional e.g. "kg" "ton"
	 */
	@ESKeyword
	public String unit;
	
	/**
	 * Extra info: Estimate for how many people were helped per output.
	 * e.g. a workshop might reach 10 people, whilst a solar lamp helps a family of ~5.
	 */
	public Double peoplePerOutput;
	
	/**
	 * Unit rate, usually per-impression (see {@link #input}).
	 * Multiplier for proportional impacts (eg "1 tree per 10,000 impressions" = 1/10000 = 0.0001)
	 */
	public Double rate;
	
	/**
	 * (feb 2023) Explicit flag for dynamic (uses rate and input) vs static
	 */
	Boolean dynamic;
	
	/**
	 * E.g. input=1000 impressions, amountRate=£0.05, then the dynamic amount would be £50
	 */
	public Money amountRate;
	
	/**
	 * Input for proportional impacts (eg "impressions", "clicks", "installs")
	 */
	@ESKeyword
	String input;
	
	/**
	 * DRAFT: this impact is still running and output blocks should be bought, 
	 * PUBLISHED: this impact is complete
	 */
	KStatus progress;
	
	/**
	 * Where does this impact calculation come from? a url
	 */
	@ESNoIndex
	String ref;
	
	@ESNoIndex
	public String description;
	
	@ESNoIndex
	String notes;

	@ESNoIndex
	SimplePlace location;
	
	/**
	 * Did the charity say something nice?
	 */
	@ESNoIndex
	Quotation quote;
	
	@ESNoIndex
	MediaObject img;
	
	@ESNoIndex
	VideoObject video;
	
	/**
	 * canonical url (e.g. Gold Standard registry)
	 */
	@ESKeyword
	String url;
	
	/**
	 * A nicer url (e.g. Ecologi project pages are nicer than Gold Standard)
	 */
	@ESKeyword
	String url2;


	private Integer roundTo;
	
	public Impact() {	
	}
	/**
	 * Copy a dynamic rate and set the amount
	 * @param impact
	 * @param co2
	 * @return 
	 */
	public Impact copyAndSet(double co2) {
		Impact copy = Utils.copy(this);
		if (amountRate==null && rate==null) {
			throw new IllegalStateException("Not a dynamic rate supplier: "+this);
		}
		if (amountRate!=null) {
			copy.amount = amountRate.multiply(co2);
		}
		if (rate!=null) {
			copy.n = rate * co2;
		}
		if (peoplePerOutput!=null) {
			copy.peoplePerOutput = peoplePerOutput;
		}
		// copy.dynamic <-- This is NOT copied because the copy is not dynamic
		// leave in, why not? as audit info
//		copy.rate = null;
//		copy.amountRate = null;
//		copy.input = null;
		return copy;
	}

	public void setDate(Time date) {
		this.date = date;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAmount(Money amount) {
		this.amount = amount;
	}
	public Money getAmount() {
		return amount;
	}
	public Money getAmountTarget() {
		return amountTarget;
	}
	
	public void setAmountTarget(Money amountTarget) {
		this.amountTarget = amountTarget;
	}

	public void setN(double n) {
		this.n = n;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public void setCharity(String id) {
		this.charity = id;
	}
	public String getCharity() {
		return charity;
	}

	@Override
	public String toString() {
		return "Impact [name=" + name + ", amount=" + amount + ", input=" + input + "]";
	}

	/**
	 * e.g. "tree(s)" This is a "canonical" name that should be managed, so we can match and add impacts.
	 * @see KImpactName
	 */	
	public String getName() {
		return name;
	}

	/**
	 * 
	 * @return e.g. 1000 for co2 kgs rounded-up to the nearest ton
	 */
	public Integer getRoundTo() {
		if (roundTo != null) return roundTo;
		if (name==null) return null;
		switch(name) {
		case KImpactName.MEALS:	case KImpactName.CORAL:	case KImpactName.TREES: 
			roundTo = 1; break;
		case KImpactName.CARBON_OFFSET: 
			roundTo = 1000; break; // kg & nearest ton
		}
		return roundTo;
	}

	/**
	 * use with KImpactName
	 * @param carbonOffset
	 * @return
	 */
	public boolean isType(String carbonOffset) {
		return getName().equals(carbonOffset);
	}

	/**
	 * Roughly, are these two copies of the same thing?
	 * @param other
	 * @return
	 */
	public boolean equiv(Impact other) {
		return Objects.equals(amount, other.amount) && Objects.equals(amountRate, other.amountRate)
				&& Objects.equals(charity, other.charity) && Objects.equals(dynamic, other.dynamic)
				&& Objects.equals(input, other.input) && Objects.equals(n, other.n) && Objects.equals(name, other.name)
				&& Objects.equals(rate, other.rate) && Objects.equals(roundTo, other.roundTo)
				&& Objects.equals(unit, other.unit);
	}
	
	
}

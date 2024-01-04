package com.winterwell.maths.stats.distributions.cond;

import static com.winterwell.utils.StrUtils.toNSigFigs;

import com.winterwell.utils.Printer;

/**
 * What info should this carry??
 * @author daniel
 *
 * @param <X>
 */
public class Explanation<X> {

	private ICondDistribution<X, Cntxt> model;
	public double forgotten;
	public double negEgs;
	public double p;
	/**
	 * The nominal total count of time we've seen this outcome.
	 * p = cnt / total-weight
	 */
	public double cnt;
	public double tpw;
	public double simplerCnt;
	/**
	 * The count from this model (before applying the forgotten adjustment)
	 */
	public double ourCnt;
	public double ourWeight;

	public Explanation(ICondDistribution<X, Cntxt> wwModel) {
		this.model = wwModel;
	}
	

	/**
	 * Explain the toString() format
	 */
	public static String doc = "[model signature], P(word|model), cnt:total-count, which comes from our-count (which will be divided by our-weight) + sub-model-count, "
			+" tpw:total-prior-weight, forgotten:count of specific egs which have been dropped (this counts as extra tpw), "
			+"neg-eg: examples from other tags count against drawing on the sub-models for support"
			+"-neg: count lost from this sub-model due to neg-egs in the parent.";
	
	/**
	 * pseudo-counts "lost" due to negative-training in the parent model.
	 */
	double neg;
	
	/**
	 * [model signature] p cnt?? tpw forgotten? eg?? neg??
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		// The model part
		sb.append(model instanceof WWModel? 
				"["+Printer.toString(((WWModel) model).getContextSignature())+"]" : model);
		// the local prob
		sb.append(" p"+toNSigFigs(p, 2));
		sb.append(" cnt:"+toNSigFigs(cnt, 2)+" ~= "+toNSigFigs(ourCnt, 2)+"/"+toNSigFigs(ourWeight, 2)+" + sub:"+toNSigFigs(simplerCnt, 2));
		sb.append(" tpw:"+toNSigFigs(tpw, 2));
		// lost due to negative learning
		if (neg!=0) sb.append(" -neg:"+toNSigFigs(neg, 2));
		
		if (forgotten!=0) sb.append(" forgotten:"+forgotten);
		if (negEgs!=0) sb.append(" neg-eg:"+negEgs);
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Explanation other = (Explanation) obj;
		if (model == null) {
			if (other.model != null) return false;
		} else if (!model.equals(other.model)) return false;
		return true;
	}

	
}

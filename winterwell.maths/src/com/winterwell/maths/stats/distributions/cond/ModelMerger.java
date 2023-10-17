package com.winterwell.maths.stats.distributions.cond;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import com.winterwell.maths.stats.distributions.IDistributionBase;
import com.winterwell.maths.stats.distributions.discrete.AFiniteDistribution;
import com.winterwell.maths.stats.distributions.discrete.Uniform;
import com.winterwell.utils.BestOne;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.Pair;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.threads.ATask;
//import com.winterwell.depot.PersistentArtifact;

/**
 * @deprecated
 * STATUS: sketch
 * 
 * Given two models, merge them if it improves Likelihood(training)
 * @author daniel
 *
 */
public class ModelMerger extends ATask<WWModel> {
	
	public void setData(Iterable<Sitn> data) {
		// we want a list so we can loop twice -- which a streaming db query wouldn't support
		this.data = Containers.getList(data);
	}
	
	WWModel main;
	WWModel candidate;
	BestOne<WWModel> best = new BestOne();
	
	List<Sitn> data;
	private String tag;

	public ModelMerger(String tag, WWModel main, WWModel candidate) {
		Utils.check4null(tag, main, candidate);
		this.tag = tag;
		this.main = main;
		this.candidate = candidate;
	}


	@Override
	protected WWModel run() {		
		double s = score(main);
		best.maybeSet(main, s);
		
		WWModel merged = union(main, candidate);
		
		if (data.isEmpty()) {
			// No data? Then assume good & accept all merges
			Log.report("ai", tag+": no data - optimistic merge", Level.INFO);
			return merged;
		}
		
		// Test on the main's data
		double score = score(merged);
		best.maybeSet(merged, score);
		
		return best.getBest();
	}




	/**
	 * Test against main's data
	 * @param model
	 * @return total likelihood
	 */
	private double score(WWModel model) {
		double totalP = 0;
		for(Sitn sitn : data) {
			double p = model.prob(sitn.outcome, sitn.context);
			totalP += p;
		}
		return totalP;
	}


	/**
	 * @param a
	 * @param b
	 * @return a new model which merges the count data from a & b.
	 * Trust data is reset.
	 * The indexex are NOT merged!
	 */
	private WWModel union(WWModel a, WWModel b) {
		return union2(a, b, new HashMap());
	}
	
	private WWModel union2(WWModel a, WWModel b, Map<Pair,WWModel> parents2merged) {
		assert Arrays.equals(a.getContextSignature(), b.getContextSignature());
		// Already done? Makes sure the DAG doesn't end up as a tree with equivalent nodes
		WWModel already = parents2merged.get(new Pair(a,b));
		if (already!=null) {
			return already;
		}

		double p = 0.5*(a.totalPriorWeight + b.totalPriorWeight);
				
		// Recurse & merge simpler models
		ICondDistribution[] aSimples = a.getSimplerModels();
		ICondDistribution[] bSimples = b.getSimplerModels();
		assert aSimples.length == bSimples.length;
		ICondDistribution[] unionSimples = new ICondDistribution[aSimples.length];
		for(int i=0; i<aSimples.length; i++) {
			ICondDistribution as = aSimples[i];
			ICondDistribution bs = bSimples[i];
			// ensure we're unwrapped
//			as = PersistentArtifact.unwrap(as);
//			bs = PersistentArtifact.unwrap(bs);
			assert as.getClass() == bs.getClass() : as+" v "+bs;
			// union??
			if (as instanceof WWModel) {				
				unionSimples[i] = union((WWModel)as, (WWModel)bs);
				
			} else if (as instanceof UnConditional) {
				IDistributionBase aBase = as.getMarginal(null);
				IDistributionBase bBase = bs.getMarginal(null);
				assert aBase.getClass() == bBase.getClass() : as+" v "+bs;
				if (aBase instanceof Uniform) {
					int s = ((Uniform) aBase).size() + ((Uniform) bBase).size();
					unionSimples[i] = new UnConditional(new Uniform(s/2));
				} else {
					throw new TodoException(as);
				}
			} else {
				// What to do?				
				throw new TodoException(as);
//				unionSimples[i] = union((WWModel)as, (WWModel)bs);
			}
		}
		
		// Now merge this one
		WWModel<?> union = new WWModel(main.getContextSignature(), main.getFixedFeatures(), 
				(a.trackedContexts+b.trackedContexts)/2, (a.trackedOutcomes+b.trackedOutcomes)/2,
				(a.trackedNegContexts+b.trackedNegContexts)/2, (a.trackedNegOutcomes+b.trackedNegOutcomes)/2,
				p, unionSimples);
		// Merge the counts!	
		// Note: the order does matter ('cos of HalfLifeMap's eviction behaviour)
		// -- this ordering favours the data in a.
		// I don't think there's anything sensible that can be done about that.
		union3_addCnts(b, union);
		union3_addCnts(a, union);
		
		parents2merged.put(new Pair(a,b), union);
		return union;

	}


	/**
	 * Add all the count data from a to that in union.
	 * @param a Unmodified
	 * @param union Will be modified
	 */
	private void union3_addCnts(WWModel a, WWModel<?> union) {
		Set<Map.Entry<Cntxt, AFiniteDistribution>> entries = a.counts.entrySet();
		for (Entry<Cntxt, AFiniteDistribution> entry : entries) {
			AFiniteDistribution a_cntxt = entry.getValue();
			AFiniteDistribution u_cntxt = union.getSpecificMarginal(entry.getKey());
			for(Object x : a_cntxt) {
				double dp = a_cntxt.prob(x);
				u_cntxt.addProb(x, dp);
			}
		}
	}
	
	
}

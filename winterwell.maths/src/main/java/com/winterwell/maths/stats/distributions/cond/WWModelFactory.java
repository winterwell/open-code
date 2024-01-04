package com.winterwell.maths.stats.distributions.cond;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.winterwell.maths.datastorage.IIndex;
import com.winterwell.maths.stats.distributions.discrete.Uniform;
import com.winterwell.utils.IFn;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.IntRange;

/**
 * Convenient creation of "standard" model types.
 * @author daniel
 * @testedby WWModelFactoryTest
 */
public class WWModelFactory<Tkn> {

	@Deprecated // Hacky test thing
	Map<List<String>,ICondDistribution> memory;

	/**
	 * Make new models.
	 * These models are fresh -- not loaded from the depot & holding no data.
	 * 
	 * @param sig feature-extractors typically use the IFns here
	 * 
	 * @param sigFixedFeatures Can be empty or null.
	 * These are poked onto the desc via {@link WWModel#setFixedFeatures(Map)}.
	 * Also used for a minor efficiency boost if all of the sig is fixed-features
	 * (which implies only 1 marginal is wanted).
	 * 
	 * @param trackedFormula outputs [trackedContexts, trackedOutcomes, neg]  e.g. 10000 * 2^-n 
	 * The input to the formula is the list of free (un-fixed) sig elements.
	 * @param tracked2TPW TPW = tracked-outcomes * this. e.g. 4 
	 * @param baseVocabSize
	 * @param sig2model Avoid dupes when recursing. Typically new HashMap() for
	 * the top-level call.
	 * @return
	 * 
	 * @see FtrLang
	 * @see FtrPrevWord
	 */
	public WWModel<Tkn> fullFromSig(
			List<String> sig, 
			Map<String,Object> sigFixedFeatures, 
			IFn<List<String>, int[]> trackedFormula,
			double tracked2TPW, 
			int baseVocabSize,
			Map<List<String>,ICondDistribution> sig2model) 
	{		
		Utils.check4null(sig, trackedFormula, sig2model);
		if (sigFixedFeatures==null) sigFixedFeatures = new ArrayMap(0);
		for (String ff : sigFixedFeatures.keySet()) {
			assert sig.contains(ff) : ff+" not in "+sig;
		}
		assert tracked2TPW > 0 && baseVocabSize > 0;
		assert tracked2TPW < baseVocabSize : "round way wrong? "+tracked2TPW+" "+baseVocabSize;
		assert tracked2TPW < 100;
		
		ArrayList freeSig = new ArrayList(sig);
		freeSig.removeAll(sigFixedFeatures.keySet());
		int[] tracked = trackedFormula.apply(freeSig);
		// Minor efficiency boost: over-ride some values for all-fixed-features
		if (Containers.contains(sig, sigFixedFeatures.keySet())) {
			// tracked = [contexts, outcomes, neg-contexts, neg-outcomes]
			tracked[0] = 1;
			tracked[2] = Math.min(tracked[2], 1);
		}
		
		double tpw = tracked[1]*tracked2TPW;
		
		// base: bag-of-words, going to uniform
		if (sig.size()==0) {						
			ICondDistribution simplest = new UnConditional(new Uniform(baseVocabSize));
			WWModel<Tkn> bagow = new WWModel<Tkn>(new String[0], sigFixedFeatures, 
					tracked[0], tracked[1], tracked[2], tracked[3],
					tpw, simplest);
			sig2model.put(sig, bagow);
			return bagow;
		}
		
		// recurse
		ICondDistribution[] simples = new ICondDistribution[sig.size()];
		for(int i=0; i<sig.size(); i++) {
			// remove the ith element
			ArrayList<String> sig2 = new ArrayList(sig);
			String sigBit = sig2.remove(i);
			assert sigBit != null;
			// ...and it's fixed value, if there was one
			ArrayMap sigMask2 = new ArrayMap(sigFixedFeatures); 
			sigMask2.remove(sigBit);
			
			// Minor efficiency hacK: removing w-1 => remove w-2 as well
			if (sigBit.startsWith("w-")) {
				Integer n = Integer.valueOf(sigBit.substring(2));
				n++;
				while(sig2.contains("w-"+n)) {
					sig2.remove("w-"+n);					
					// ...and it's fixed value, if there was one					
					sigMask2.remove("w-"+n);		
					n++;
				}
			}
			
			// here's one I made earlier?
			ICondDistribution simpler = sig2model.get(sig2);			
			if (simpler==null) {
				// do the recursion...
				simpler = fullFromSig(sig2, sigMask2, 
						trackedFormula,
						tracked2TPW, baseVocabSize,
						sig2model);
			}
			simples[i] = simpler;
		}
		
		// Make a model
		WWModel<Tkn> model = new WWModel(sig.toArray(new String[0]), sigFixedFeatures, 
				tracked[0], tracked[1], tracked[2], tracked[3],
				tpw, simples);
		sig2model.put(sig, model);
		return model;
	}
	
	/**
	 * Use with {@link StreamClassifier} to get Naive-Bayes
	 * @param index
	 */
	public <X> WWModel<X> bagOfWords(IIndex<X> index) {
		String[] signature = new String[0];
		ICondDistribution simple = new UnConditional<X>(new Uniform(index));
		int tc = index.size();
		int to = tc;
		int ntc = Math.max(4, tc/100);
		int nto = ntc;
		WWModel<X> model = new WWModel(signature, Collections.EMPTY_MAP, tc, to, ntc, nto, 2*index.size(), simple);
		return model;
	}
	
	/**
	 * Use with {@link StreamClassifier} to get Naive-Bayes.
	 * TPW = 2 * vocabSize, neg-contexts = vocabSize/100
	 * @param vocabSize
	 */
	public <X> WWModel<X> bagOfWords(int vocabSize) {
		String[] signature = new String[0];
		ICondDistribution simple = new UnConditional<X>(new Uniform(vocabSize));
		int tc = 1;
		int to = vocabSize;
		int ntc = 1;
		int nto = Math.max(4, tc/100);
		WWModel model = new WWModel(signature, Collections.EMPTY_MAP, tc, to, ntc, nto, 2*vocabSize, simple);
		return model;
	}

	
	/**
	 * Get a formula for how many [contexts, outcomes, neg-contexts, neg-outcomes] to track.
	 * The input to the formula is the list of free (un-fixed) sig elements.
	 * 
	 * @param a e.g. 100k The most outcomes for the least specific level. 
	 * @param b e.g. 2 to double each level The growth rate.
	 * @param negRatio e.g. 100 for 100 less negative contexts than positive ones.
	 * @param contextOutcomeRatio e.g. 2 How many contexts to track relative to outcomes-per-context
	 * cap = [10, a] on outcomes, [5, a/contextOutcomeRatio] on contexts
	 * @return tracked-contexts = 0.5 * tracked-outcomes = a / b^sig.size (with a minimum of 10 outcomes, 5 contexts)<br>
	 * 			negative-tracked = tracked / negRatio (min:10,5) 
	 */
	public IFn<List<String>, int[]> trackedFormula(final double a, final double b, final double negRatio, final double contextOutcomeRatio) 
	{
		assert a>0 && b > 0 : a+" "+b;
		assert negRatio > 1;
		final IntRange cap = new IntRange(10, (int)a);
		assert cap.low > 0 : cap;
		return new IFn<List<String>, int[]>() {
			@Override
			public int[] apply(List<String> sig) {
				// tracked = e.g. n / 2^sig (so it doubles each step less specific we get)
				double _to = a*Math.pow(b, - sig.size());
				int to = cap.cap(_to);
				int tc = (int) Math.round(to/contextOutcomeRatio);
				tc = Math.max(tc, 5); //??
				int ntc = (int) Math.max(tc/negRatio, 5); //??
				int nto = (int) Math.max(to/negRatio, 10);
				return new int[]{tc, to, ntc, nto};		
			}
		};
	}

	/**
	 * Convenience for testing. Uses "sensible" values.
	 */
	public WWModel<Tkn> fullFromSig(List<String> sig) {
		double tracked2TPW = 2;
		IFn<List<String>, int[]> trackedFormula = trackedFormula(10000, 2, 100, 2);
		return fullFromSig(sig, null, trackedFormula, tracked2TPW, 10000, memory==null? new HashMap() : memory);
	}
	
	/**
	 * Convenience for testing. Uses "sensible" values.
	 */
	public WWModel<Tkn> fullFromSig(List<String> sig, Map<String,Object> fixedFeatures) {
		double tracked2TPW = 2;
		IFn<List<String>, int[]> trackedFormula = trackedFormula(10000, 2, 100, 2);
		return fullFromSig(sig, fixedFeatures, trackedFormula, tracked2TPW, 10000, memory==null? new HashMap() : memory);
	}

	/**
	 * HACK for testing (though it may well get promoted to being the standard, if we fix the inherent issues)
	 * @param b
	 */
	@Deprecated
	public void setMemory(boolean b) {
		memory = b? new HashMap() : null;
	}
	
}

package com.winterwell.maths.stats.distributions.cond;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.winterwell.depot.Desc;
import com.winterwell.depot.IHasDesc;
import com.winterwell.depot.ModularXML;
import com.winterwell.maths.ITrainable;
import com.winterwell.maths.datastorage.HalfLifeMap;
import com.winterwell.maths.datastorage.IForget;
import com.winterwell.maths.graph.DiEdge;
import com.winterwell.maths.graph.DiGraph;
import com.winterwell.maths.graph.DiNode;
import com.winterwell.maths.graph.IGraph;
import com.winterwell.maths.stats.distributions.IDistributionBase;
import com.winterwell.maths.stats.distributions.d1.MeanVar1D;
import com.winterwell.maths.stats.distributions.discrete.AFiniteDistribution;
import com.winterwell.maths.stats.distributions.discrete.IFiniteDistribution;
import com.winterwell.maths.stats.distributions.discrete.ObjectDistribution;
import com.winterwell.maths.stats.distributions.discrete.Uniform;
//import com.winterwell.depot.PersistentArtifact;
import com.winterwell.utils.Key;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.time.Time;

/**
 * The Winterwell text model.
 *  
 * <h3>Thread Safety</h3>
 * The train() methods are synchronized. This allows for multi-threaded training _and_ saving
 * via XStream serialisation.
 * 
 * 
 * @param <X> probabilities over X (e.g. X=String or Tkn for words)
*/
//@ModularXML
public class WWModel<X> extends ACondDistribution<X, Cntxt>
implements 
Cloneable, ITrainable.Supervised<Cntxt,X>, IHasDesc, ModularXML,
ICondDistribution.WithExplanation<X, Cntxt>, 
//INotOverwritable, we can merge now 
IHasSignature
{

	private static final String LOGTAG = "WWModel";

	@Override
	public IHasDesc[] getModules() {
		List<IHasDesc> ms = new ArrayList(simples.length);
		for(ICondDistribution m : simples) {
			// in principle, it's not just WWModels which could use modular xml
			if (m instanceof ModularXML) {
				ms.add((IHasDesc) m);
			}
		}
		return ms.toArray(new IHasDesc[0]); 
	}
	
	@Override
	protected WWModel clone() throws CloneNotSupportedException {
		ICondDistribution<X, Cntxt>[] simples2 = Arrays.copyOf(simples, simples.length);
		WWModel clone = new WWModel(signature, fixedFeatures,
				trackedContexts, trackedOutcomes, trackedNegContexts, trackedNegOutcomes,
				totalPriorWeight, simples2);
		// copy the setup
		clone.counts.putAll(counts);
		System.arraycopy(simplesWeights, 0, clone.simplesWeights, 0, simplesWeights.length);
		System.arraycopy(this.simplesWeightsNorm, 0, clone.simplesWeightsNorm, 0, simplesWeightsNorm.length);
		return clone;
	}
	
	public String getDebugInfo_topMarginals() {
		WWModelDescription wwmd = new WWModelDescription((WWModel) this, 10);
		String s = wwmd.toString();
		if (Utils.isBlank(s)) {
			s = wwmd.toString(); // DEBUG
		}
		return s;
	}
	


	public String getDebugInfo_contexts() {
		WWModelDescription wwmd = new WWModelDescription((WWModel) this, 10);
		String s = wwmd.getTopContextsString();
		if (Utils.isBlank(s)) {
			s = wwmd.getTopContextsString(); // DEBUG
		}
		return s;		
	}

	
	public String getDebugInfo() {
		Desc<WWModel> d = getDesc();
//		MetaData md = Depot.getDefault().getMetaData(d);
//		long size = md.getFile().exists()? md.getFile().length() : 0;
		// name
		String name = d.getName();			
		Object sample = getDebugInfo_topMarginals();
		Object contexts = getDebugInfo_contexts();
		// not
//		Object notThis = getNotLearning(artifact);
		// tracking
		Collection<AFiniteDistribution<X>> marginals = getMarginals();
		MeanVar1D mv = new MeanVar1D();
		for (AFiniteDistribution marg : marginals) {
			mv.train1(1.0*marg.size());
		}
		String tracking = Printer.toString(getTracking())+" using contexts: "
					+getContexts().size()
					+" outcomes: "+mv
					+" neg: "+getNegContexts().size();		
		double cnt = getTrainingCnt();
//			if (cnt>0) totalTraining += cnt;
		// add the row				
		return com.winterwell.utils.StrUtils.joinWithSkip(", ", 
					name, 
					"cnt:"+StrUtils.toNSigFigs(cnt, 2), 
//					"size:"+FileUtils.size(md.getFile()), 
					"sample:"+sample, 
					"contexts:"+contexts, 
//					notThis,
					"tracking:"+Printer.toString(tracking)
//					"file:"+md.getFile()
					);
	}
	
	/**
	 * Negative training -- an example of what this model is _not_.
	 * @param context
	 * @param outcome
	 * @param sigPart e.g. "tag" Negative training is done versus some other model. 
	 * This is the part of the signature which differs. If this isn't part of the signature, it means we're onto a generic
	 * model (which should nevertheless receive some, but less, negative training c.f. WWModel.txt).
	 * @param sigValue The value for sigPart (kind of superfluous;
	 * here as a sanity check)
	 * @param weight 
	 */
	public synchronized void train1Not(Cntxt context, X outcome, String sigPart, String sigValue, double weight) {
		Utils.check4null(context, outcome, sigPart, sigValue);
		// Are we bothered?
		if (trackedNegContexts==0) return;
		// threshold -- too low weights are pointless
		if (weight < 0.001) return;
		// Does this affect us directly? If this is a generic model, weight it weaker
		if ( ! Containers.contains(sigPart, signature)) {
			// TODO what should this discount be? It could arguably be nothing, or number-of-tags. 
			// 2 seems a safe place to start, although it will mean for more complex models, the generic
			// lower levels will get past negative training..
			train1Not2(sigPart, context, outcome, new ArrayList(), weight*GENERIC_GETS_SOME_NEGATIVE_TRAINING);
			return;
		}
		train1Not2(sigPart, context, outcome, new ArrayList(), weight);
	}
		
	/**
	 * [tracked-contexts, tracked-outcomes, negative-tracked-contexts, negative-tracked-outcomes]
	 */
	private static final Key<int[]> DESC_TRACKING = new Key("tr");
	private static final Key<Double> DESC_PRIOR_WEIGHT = new Key("TPW");
	private static final Key<String[]> DESC_SIG = new Key("sig");

	/**
	 * A pseudocount of points assigned to simpler models. This prevents a
	 * poorly performing simpler model being killed off too quickly. Buggered if
	 * I know what a sensible value is.
	 */
	private static final double SIMPLER_MIX_PSEUDO_COUNT = 3;
	
	/**
	 * HACK: the sig name for tag. This is used in the sig of tag-specific models.
	 */
	public static final String SIG_TAG = "tag";
	private static final double GENERIC_GETS_SOME_NEGATIVE_TRAINING = 0.5;

	/**
	 * The ideal size for the number of context specific-marginals to keep here.
	 * This should be a bit higher than the number of context you expect to matter. 
	 */
	int trackedContexts;

	/**
	 * Used via {@link #getTotalPriorWeight()}.
	 * This is a constant assignment of weight to the prior, not a cached
	 * sum of the prior's internal weight.
	 */
	final double totalPriorWeight;

	/**
	 * context -> specific marginal
	 * <p>
	 * NB: the specific marginal may be a plain ObjectDistribution
	 * or may contain it's own chain of models.
	 */
	final Map<Cntxt, AFiniteDistribution<X>> counts;
	final Map<Cntxt, AFiniteDistribution<X>> negCounts;

	/**
	 * The "fields" which get used as context. Never null.<br> 
	 * An empty array for Naive Bayes.<br>
	 * ["previous word"] (or similar) for a classic markov-chain.
	 * @warning: The order is important!
	 */
	final String[] signature;

	/**
	 * null for the blind uniform model
	 */
	final ICondDistribution<X, Cntxt>[] simples;

	/**
	 * The relative weight to give each simpler model. The units are total
	 * counts of claimed training-data examples.
	 * <br>
	 * This is used to decide between simpler models.
	 * It is _not_ related to {@link #getTotalPriorWeight()}
	 * 
	 * @see notes in LatticeModel.txt
	 */
	final double[] simplesWeights;

	/**
	 * Relative share of total-prior-weight. This sums to the value of
	 * {@link #getTotalPriorWeight()}, and is proportional to {@link #simplesWeights}.
	 */
	final double[] simplesWeightsNorm;
	/**
	 * Count of training data seen.
	 */
	double trainCnt;
	// for debugging
	Time created = new Time();
	
	public double getTrainingCnt() {
		return trainCnt;
	}
	
	private Desc<WWModel> desc;
	
	/**
	 * The number of outcomes *per-context* to track.
	 */
	int trackedOutcomes;

	int trackedNegContexts;
	int trackedNegOutcomes;

	/**
	// Note: negCnt = 1 doesn't mean much! Just that we've seen that word in another tag's tweet.
	// NB: neg-counters are typically small & very forgetful -- so they can't learn much.
	// Hm... 
	// A "typical" simplerCnt might be ~1
	// But you can easily get negCnt of ~10 from just a few repetitive examples.
	// HACK #1: Use e^-x to lower the effect.
	// TODO investigate a more theoretically based solution!															
	negCntEffect = Math.pow(negEffect, -negCnt);
	*/
	double negEffect = 1.1;
	
	
	public WWModel(String[] signature, Map<String,Object> fixedFeatures,
			int trackedContexts,
			int trackedOutcomes,
			int trackedNegContexts,
			int trackedNegOutcomes,
			double totalPriorWeight, 
			ICondDistribution<X, Cntxt>... simples)
	{
		assert signature != null && simples.length != 0;
		assert totalPriorWeight > 0;		
		assert simples.length != 0;				
		this.simples = simples;
		this.signature = signature;
		assert signature != null;
		this.fixedFeatures = fixedFeatures==null? Collections.EMPTY_MAP : fixedFeatures;
		this.totalPriorWeight = totalPriorWeight;
		// safety check
		for(ICondDistribution c : this.simples) {
			assert c != null : this;
			checkSignature(c);
		}
//		Log.d("WWModel", Printer.toString(signature)+" simples:"+Printer.toString(this.simples));
		// How much shall we track?
		setTracking(new int[]{trackedContexts, trackedOutcomes, trackedNegContexts, trackedNegOutcomes});
		assert trackedContexts > 0 && trackedOutcomes > 0;
		// weights
		simplesWeights = new double[simples.length];
		simplesWeightsNorm = new double[simples.length];
		for (int i = 0; i < simples.length; i++) {
			simplesWeights[i] = SIMPLER_MIX_PSEUDO_COUNT;
		}
		updateSimpleWeightsNorm();
		
		// for NaiveBayes, we use a singleton-map, otherwise a thread-safe hash-map
		
		// TODO don't use 1 counts size for all contexts??
		// In highly specific contexts -- although there will be a larger set of possible
		// contexts, many of them will never be seen enough to matter. So perhaps we only 
		// need to track a smaller set, counter-intuitive though that is?
		
		if (signature.length == 0) {
			// this is a bag-of-words			
			counts = Collections.singletonMap(
					Cntxt.EMPTY, newMarginal(Cntxt.EMPTY));
			negCounts = trackedNegContexts==0? null : Collections.singletonMap(Cntxt.EMPTY, newMarginal(Cntxt.EMPTY));
		} else if (trackedContexts<2) {
			// we have only fixed features, and expect a single context (but we don't know what the context looks like right here)
			counts = new ArrayMap(1);
			negCounts = trackedNegContexts==0? null : new ArrayMap(1);
		} else {
			counts = //Collections.synchronizedMap( HalfLifeMap is thread-safe
						new HalfLifeMap(trackedContexts);
				// ?? new ConcurrentHashMap<Cntxt, ObjectDistribution<X>>();
			negCounts = trackedNegContexts==0? null : 
						new HalfLifeMap(trackedNegContexts);
		}
		
		// save some memory
		noTrainingDataCollection();
	}

	@Override
	public void finishTraining() {
		// nothing to do
	}

	
	/**
	 * 
	 * @return Just a recursive get. These are the in-memory models. No mucking about with Depot.
	 */
	public Set<ICondDistribution<X, Cntxt>> getAllSimplerModels() {
		if (simples == null)
			return Collections.emptySet();
		Set<ICondDistribution<X, Cntxt>> models = new HashSet();
		for (ICondDistribution<X, Cntxt> m : simples) {
			if (m==null) throw new NullPointerException("null simpler model in "+this);
			models.add(m);
			if (m instanceof WWModel) {
				Set<ICondDistribution<X, Cntxt>> grandkids = ((WWModel<X>) m).getAllSimplerModels();
				models.addAll(grandkids);
			}
		}
		return models;
	}

	
	public Set<Cntxt> getContexts() {
		return counts.keySet();
	}
	
	/**
	 * You typically need to add extra info to this! E.g. what tag is this for?
	 * 
	 * @deprecated Use with care!
	 */
	public Desc<WWModel> getDesc() {
		if (desc!=null) {
			return desc;
		}
		assert signature!=null : this;
		String name = StrUtils.join(signature, "+");
		if (name.isEmpty()) name = "∅";
		desc = new Desc<WWModel>(name, WWModel.class);
		desc.put(DESC_SIG, signature);
		desc.put(DESC_TRACKING, new int[]{
			trackedContexts, trackedOutcomes,
			trackedNegContexts, trackedNegOutcomes
		});
		desc.put(DESC_PRIOR_WEIGHT, totalPriorWeight);
		
		// Dependencies... 
		for(int s=0; s<simples.length; s++) {
			ICondDistribution<X, Cntxt> d = simples[s];
			Desc depDesc = Desc.desc(d);
			if (depDesc!=null) {
				desc.addDependency("s"+s, depDesc);				
			}
		}
		// set each fixed feature as a model property
		if (fixedFeatures!=null) {
			for (String k : fixedFeatures.keySet()) {
				// ?? check we don't overwrite something else?? 
				desc.put(k, fixedFeatures.get(k));
			}
		}
		return desc;
	}


	/**
	 * The specific marginal + the prior.<br>
	 * The key-set is a snapshot from when the method is called. 
	 * Probabilities are calculated "live" from this model.
	 * <p>
	 * @deprecated For information use only -- the lack of guarantees
	 * around the tags includes makes this dangerous.
	 * <p>
	 * @WARNING: size and iterator may not be inclusive!
	 */
	@Override
	public AFiniteDistribution<X> getMarginal(final Cntxt context) {
		final WWModel<X> wwm = this;
		// Collect all the tags
		AFiniteDistribution<X> sm = getSpecificMarginal(context);
		final Set<X> tags = new HashSet(sm.asList());
		for(ICondDistribution<X, Cntxt> s : getAllSimplerModels()) {
			try {
				IDistributionBase<X> smarginal = s.getMarginal(context);
				if (smarginal instanceof IFiniteDistribution) {
					List stags = Containers.getList((IFiniteDistribution)smarginal);
					tags.addAll(stags);
				}
			} catch(UnsupportedOperationException ex) {
				// ignore
			}
		}
		// add in more tags
		return new AFiniteDistribution<X>() {
			@Override
			public Iterator<X> iterator() throws UnsupportedOperationException {
				return tags.iterator();
			}

			@Override
			public int size() {
				return tags.size();
			}

			@Override
			public double prob(X x) {
				return wwm.prob(x, context);
			}
		};
	}


	public Collection<AFiniteDistribution<X>> getMarginals() {
		return counts.values();
	}

	
	/**
	 * The "fields" which get used as context. Never null.<br> 
	 * An empty array for Naive Bayes.<br>
	 * ["w-1"] (i.e. previous word, or similar) for a classic markov-chain.
	 */
	public String[] getContextSignature() {
		return signature;
	}

	
	public ICondDistribution<X, Cntxt>[] getSimplerModels() {
		return simples;
	}

	
	public double[] getSimplerWeights() {
		return simplesWeights;
	}
 
	
	/**
	 * WARNING: This is not the true marginal - because it doesn't include the
	 * prior!
	 * @return never null - creates a new one if needed.
	 */	
	public AFiniteDistribution<X> getSpecificMarginal(Cntxt context) {
		context = pareDown(context);
		AFiniteDistribution<X> m = counts.get(context);
		if (m == null) {
			m = newMarginal(context);
			m.setRandomSource(random());
			((ITrainable)m).resetup();
			counts.put(context, m);
		}
		return m;
	}
	
	AFiniteDistribution<X> getNegMarginal(Cntxt context) {
		context = pareDown(context);
		AFiniteDistribution<X> m = negCounts.get(context);
		if (m == null) {
			// Memory issue: we need a distro that won't grow unboundedly.
			// So we use HalfLifeMap which will self-prune
			HalfLifeMap<X,Double> map = new HalfLifeMap(trackedNegOutcomes);
//			map.setTrackPrunedValue(true); Should we track pruning more for debug/tuning analysis??
			m = new ObjectDistribution<X>(map, false);
			m.setRandomSource(random());
			((ITrainable)m).resetup();
			negCounts.put(context, m);
		}
		return m;
	}

	protected AFiniteDistribution<X> newMarginal(Cntxt context) {
		// Memory issue: we need a distro that won't grow unboundedly.
		// So we use HalfLifeMap which will self-prune
		HalfLifeMap<X,Double> map = new HalfLifeMap(trackedOutcomes);
		map.setTrackPrunedValue(true);
		ObjectDistribution distro = new ObjectDistribution<X>(map, false);
		return distro;
	}

	/**
	 * This is the un-normalised probability weight assigned to all the
	 * sub-models. This determines how quickly the model trusts
	 * itself over it's sub-models.
	 */
	protected final double getTotalPriorWeight() {
		return totalPriorWeight;
	}


	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public double logProb(X outcome, Cntxt context) {
		return Math.log(prob(outcome, context));
	}

	/**
	 * Remove elements from context that don't match our signature. Must NOT be
	 * called on the blind uniform distribution.
	 * 
	 * @param context
	 * @return
	 */
	Cntxt pareDown(Cntxt context) {
		// TODO return context.pareDown(context, signature, fixedFeatures) 
		// Not done yet 'cos potential for branch version errors
		// right length anyway?
		if (context.bits.length == signature.length && Utils.isEmpty(fixedFeatures)) {
			assert Arrays.equals(signature, context.sig)
						: context+" v "+Printer.toString(signature);
			return context;
		}
		// make a simpler context
		Object[] bits2 = new Object[signature.length];
		int csi=0;
		for(int msi=0; msi<signature.length; msi++) {
			String msis = signature[msi];
			if (fixedFeatures!=null) {
				Object v = fixedFeatures.get(msis);	
				if (v!=null) {
					bits2[msi] = v;
					continue;
				}
			}
			boolean ok = false;
			for(; csi<context.sig.length; csi++) {
				String csis = context.sig[csi];
				if (msis.equals(csis)) {
					bits2[msi] = context.bits[csi];
					ok = true;
					break;
				}
			}
			if ( ! ok) throw new IllegalArgumentException(
					"sig:"+Printer.toString(signature)+" Original:"+context+" Pared:"+bits2+" "+getDesc());
		}		 
		Cntxt sitn = new Cntxt(signature, bits2);
		return sitn;
	}
	

	@Override
	public double prob(X x, Cntxt context) {
		return probWithExplanation(x, context, null);
	}
	
	
	/**
	 * 
	 * @param x
	 * @param context
	 * @param explain A DiGraph<Explanation<X>>, or null
	 * @return
	 */
	public double probWithExplanation(X x, Cntxt context, ExplnOfDist explainOfDist) {
		// create an explanation if wanted (& not yet created)
		DiNode<Explanation<X>> node = null;
		DiGraph explain = null;
		if (explainOfDist!=null) {
			explain = explainOfDist.graph();
			Explanation<X> expln = new Explanation<X>(this);
			node = explain.getNode(expln);
			if (node==null) {
				node = explain.addNode(expln);	
			}
		}
		
		context = pareDown(context); // throw away unwanted info
		AFiniteDistribution<X> counter = counts.get(context);
		// the total weight = all counts in this model's marginal
		// + all counts from the sub-model's marginals (added later)
		final double thisModelsWeight = counter==null? 0 : counter.getTotalWeight();		
		double totalWeight = thisModelsWeight;

		// cnt is sort-of in units of "examples seen". We'll divide by total-examples to get p
		final double thisModelsCnt = counter==null? 0 : counter.prob(x);
		double cnt = thisModelsCnt;
		if (node!=null) {
			node.getValue().ourCnt = thisModelsCnt;
			node.getValue().ourWeight = thisModelsWeight;
		}
				
		// Lower the strength of the count to reflect forgotten examples
		// (equivalent to boosting the TPW -- we generously assume the simpler models have not forgotten)
		double forgottenCount = counter==null? 0 : getMarginalForgottenCount(counter);
		if (forgottenCount != 0) {
			double f = totalWeight/(totalWeight+forgottenCount);
			// cap the strength of the effect in case of bad-behaviour
			f = Math.max(f, 0.25);
			cnt *= f;
			totalWeight *= f;
			if (node!=null) {
				node.getValue().forgotten = forgottenCount;
			}
		}		
		
		// What do the simpler models contribute?
		double simplerCnt=0, simplerWeight=0;
		// threshold small contributions for a bit of efficiency
		double threshold = totalWeight * 0.001;
		// Lower the TPW by any negative counts?
		double fracAfterNeg = fractionAfterNegCounts(x, context, node);
		assert MathUtils.isProb(fracAfterNeg);
		
		for (int i = 0; i < simples.length; i++) {
			double wi = simplesWeightsNorm[i];
			
			if (wi < threshold) {
				continue;
			}
			ICondDistribution<X, Cntxt> model = simples[i];

			// this is a normalised prob
			double p;
			if (model instanceof ICondDistribution.WithExplanation) {
				p = ((ICondDistribution.WithExplanation)model).probWithExplanation(x, context, explainOfDist);	
			} else {
				p = model.prob(x, context);
			}
			// simpler count?
			double sCnt = wi*p;		double sWt = wi; 
			
			// Apply the negative effect? Only to untagged models.
			// (because the rationale is that negative examples detract from the pseudo-counts
			// given by the general models which could be attributable to this tag)
			double lost = 0; // NB: keep the count for the explanation 
			if (fracAfterNeg!=1 && ! isTagged(model)) {
				// The pseudo-count is shrunk!
				lost = sCnt*(1-fracAfterNeg);
				sCnt -= lost;
				sWt -= lost; // NB: reducing sWt is likely to have bugger all effect, but it's correct so why not
			}
			
			// We multiply by the sub-model's share of the total prior weight
			// to get something comparable with this model's counter.
			simplerCnt += sCnt;
			simplerWeight += sWt;
			
			// explain: how important was this child model?
			if (explain!=null) {
				Explanation<X> simpleExplanation = new Explanation(model);
				DiNode<Explanation<X>> simpleNode = explain.getNode(simpleExplanation);
				if (simpleNode==null) {
					simpleNode = explain.addNode(simpleExplanation);	
				}				
				// pass down the lost info (although technically it relates to the link)
				simpleExplanation.neg = lost;
				assert simpleNode != null : model;
				// is this link old news?
				DiEdge edge = explain.getEdge(node, simpleNode);
				if (edge==null || edge.getWeight() != wi) {
					// Make a link & store the weight
					edge = explain.addEdge(node, simpleNode, null);
					edge.setWeight(wi);
				}
			}
		}
		
		// add the simpler scores to this model's scores
		cnt += simplerCnt;
		totalWeight += simplerWeight;
		
		assert totalPriorWeight != 0;
		assert totalWeight != 0 : Printer.toString(simplesWeightsNorm)
				+ " from " + Printer.toString(simplesWeights);
		// normalise for this context
		double p = cnt / totalWeight;
		assert MathUtils.isProb(p) : p + "	= " + cnt + "/" + totalWeight;
		
		if (node!=null) {
			node.getValue().p = p;
			node.getValue().cnt = cnt;
			node.getValue().simplerCnt = simplerCnt;
			node.getValue().tpw = simplerWeight;
		}
		
		// done
		return p;
	}


	/**
	 * HACK: is this a tag-specific model?
	 * @param model
	 * @return true if model.sig contains "tag"
	 */
	private boolean isTagged(ICondDistribution<X, Cntxt> model) {
		if (model instanceof WWModel) {
			WWModel wwm = (WWModel) model;
			String[] sig = wwm.getContextSignature();
			boolean tagged = Containers.contains(SIG_TAG, sig);
			return tagged;
		}
		return false;
	}

	/**
	 * Negative examples reduce the chance of a simpler model producing 
	 * this outcome.
	 * @param node 
	 * @param context 
	 * @return [0,1] 1 if there is no negative learning. Should usually be close to 1. 
	 */
	private double fractionAfterNegCounts(X x, Cntxt context, DiNode<Explanation<X>> node) {
		if (negCounts==null) return 1;
		AFiniteDistribution<X> negs = negCounts.get(context);
		if (negs==null) return 1;
		// How often has this outcome been seen -- by a rival model?
		double negCnt = negs.prob(x);
		if (negCnt==0) return 1;
		assert negCnt > 0;
		// Note: negCnt = 1 doesn't mean much! Just that we've seen that word in another tag's tweet.
		// NB: neg-counters are typically small & very forgetful -- so they can't learn much.
		// Hm... 
		// A "typical" simplerCnt might be ~1
		// But you can easily get negCnt of ~10 from just a few repetitive examples.
		// HACK #1: Use e^-x to make the effect relative?
		// TODO investigate a more theoretically based solution!
		// E.g. with negEffect=1.1, a negCnt of 0 has no effect, 1 = keep 91%, 2=83%, 10=39%
		double negCntEffect = Math.pow(negEffect, -negCnt);
		assert MathUtils.isProb(negCntEffect);
		// Explain it?
		if (node!=null) {
			node.getValue().negEgs = negCnt;
		}
		return negCntEffect;		
	}

	/**
	 * @param counter
	 * @return total count of forgotten counts, if we know this, or 0 otherwise
	 */
	double getMarginalForgottenCount(AFiniteDistribution<X> counter) {
		if (counter instanceof ObjectDistribution) {
			Map map = ((ObjectDistribution) counter).getBackingMap();
			if (map instanceof IForget) {
				IForget hlm = (IForget) map;
				double pv = hlm.getPrunedValue();
				if ( ! Double.isNaN(pv)) {
					assert pv >= 0 : pv+" "+this;
					return pv;
				}
			}
		}
		return 0;
	}

	
	public void removeMarginal(Cntxt context) {
		counts.remove(context);
	}

	@Override
	public void resetup() {
		super.resetup();
		// Network weights
		for (int i = 0; i < simples.length; i++) {
			simplesWeights[i] = SIMPLER_MIX_PSEUDO_COUNT;
		}
		updateSimpleWeightsNorm();
		// Counts
		counts.clear();
		trainCnt = 0;
		return;
	}

	@Override
	public X sample(Cntxt context) {
		context = pareDown(context);
		// pick this or a sub-model
		ObjectDistribution<ICondDistribution<X,Cntxt>> mix = new ObjectDistribution();
		AFiniteDistribution<X> marginal = counts.get(context);
		if (marginal != null) {
			mix.setProb(this, marginal.getTotalWeight());
		}
		
		for (int i = 0; i < simples.length; i++) {
			ICondDistribution<X, Cntxt> m = simples[i];
						
			mix.setProb(m, simplesWeightsNorm[i]);
		}
		
		if (mix.getTotalWeight() == 0) {
			// no weight
			Log.report("sample", "No weight in "+this, Level.WARNING);
			return null;
		}
		
		ICondDistribution<X, Cntxt> m = mix.sample();
		
		if (m == this)
			return marginal.sample();
		
		// Special case handling of the blind Uniform which can't support sample
		if (m instanceof UnConditional) {
			UnConditional uncon = (UnConditional) m;
			if (uncon.dist instanceof Uniform) {
				Uniform uni = (Uniform) uncon.dist;
				Iterable base = uni.getBase();
				if (base==null) {
					return sample2_noBase(context, marginal);
				}
			}
		}

		// recurse
		return m.sample(context);
	}

	protected X sample2_noBase(Cntxt context, AFiniteDistribution<X> marginal) {
		if (marginal!=null && ! marginal.isEmpty()) {
			Object[] keys = marginal.toArray();
		    int i = random().nextInt(keys.length);
		    return (X) keys[i];
		}
		Log.w("nlp", "Sampling failed for "+this+" in "+context);
		return null;
	}

	@Override
	public String toString() {
		return "WWModel[" + Arrays.toString(signature)+" training-cnt:"+getTrainingCnt()+"]";
	}

	@Override
	public synchronized void train1(Cntxt context, X outcome, double weight) {
		train12(weight, context, outcome, new ArrayList());
	}
	
	
	/**
	 * 
	 * @param weight Weighted training -- for defending against overly-specific training data 
	 * @param context
	 * @param outcome
	 * @param done
	 */
	void train12(double weight, Cntxt context, X outcome, List<ICondDistribution<X,Cntxt>> done) 
	{
		Log.v(LOGTAG, "train ",this," w ",context,"=",outcome);
		assert MathUtils.isProb(weight) : weight;
		assert weight > 0 : "No weight? "+this+" "+context+" "+outcome;
		// Prevent double-training in the lower-level models
		if (done.contains(this))
			return;
		done.add(this);
		trainCnt += weight;
		
		// chop the context down to our size
		context = pareDown(context);
		assert context.bits.length == signature.length : context + " vs "
				+ signature;
		
		// up the counts
		AFiniteDistribution<X> counter = getSpecificMarginal(context);
		counter.addProb(outcome, 1);		
		
		// Train the simpler models!
		double[] simplerProb = new double[simples.length];
		double spTotal = 0;
		// ...limit how much the more-general models learn from specific situations
		// (this helps guard against overly narrow training data)
		double passDownFactor = passDownFactor(counter);
		double passDownWeight = weight * passDownFactor;
		assert passDownWeight <= weight;
		assert passDownWeight > 0 : "No pass down? "+weight+" "+passDownFactor+" "+this+" "+context;
		
		for (int i = 0; i < simples.length; i++) {
			ICondDistribution<X, Cntxt> sm = simples[i];
			// for rating the simpler models
			double p = sm.prob(outcome, context);
			simplerProb[i] = p;
			spTotal += p;
			
			// train?
			if (sm instanceof WWModel) {
				((WWModel)sm).train12(passDownWeight, context, outcome, done);
			} else if (sm instanceof ITrainable.CondUnsupervised) {
				if ( ! done.contains(sm)) {
					((ITrainable.CondUnsupervised)sm).train1(context, outcome, passDownWeight);
					done.add(sm);
				}
			}
		}
		
		// rate the simpler models
		if (spTotal != 0) {
			for (int i = 0; i < simplerProb.length; i++) {
				simplesWeights[i] += simplerProb[i] / spTotal;
			}
			updateSimpleWeightsNorm();
		}		
	}

	boolean fullPassDown;
	
	/**
	 * 	 * Are some of the signature elements fixed-by-the-model?
	 * E.g. "tag" often is.
	 * Edits the description to show this, so different values will be stored
	 * separately in the depot.

	 * Can be empty, never null.
	 */
	private final Map<String, Object> fixedFeatures;
	
	// TODO This is probably higher than optimal
	private static double tagBoost = 3;

	public double getTagBoost() {
		return tagBoost;
	}
	/**
	 * See doc notes "Trust Yourself" for why generic models can be too strong.
	 * This counteracts that.
	 * @param tagBoost 1 (no effect) or higher. Default is 2
	 */
	public void setTagBoost(double tagBoost) {
		assert tagBoost >= 1.0 : tagBoost; 
		if (this.tagBoost==tagBoost) return;
		this.tagBoost = tagBoost;
		getDesc().put("tagBoost", tagBoost);

	}
	
	/**
	 * Are some of the context features fixed for this model?
	 * @return Can be null
	 * @see #setFixedFeatures(Map).
	 * NB: This is only used for error-checking.
	 */
	public Map<String, Object> getFixedFeatures() {
		return fixedFeatures;
	}
	
	/**
	 * The passdown system reduces the amount of training given to simpler
	 * models for familiar situations. This helps guard against overly
	 * specific training data. 
	 * @param fullPassDown true to switch pass-down filtering off
	 * (i.e. true => full training of simpler models). false by default.
	 */
	public void setFullPassDown(boolean fullPassDown) {
		this.fullPassDown = fullPassDown;
		// don't bother storing the default (null=don't store)
		Desc<WWModel> _desc = getDesc();
		_desc.unset();
		_desc.put("psdwn", fullPassDown? null : fullPassDown);
	}

	/**
	 * @param counter
	 * @return (forgotten + TPW) / (forgotten + TPW + the-specific-model-weight)
	 */
	private double passDownFactor(AFiniteDistribution<X> counter) {
		if (fullPassDown) return 1;
		// TODO because we can't easily cache the total weight (due to HalfLifeMap's pruning),
		// it means this is an expensive call :(
		double specificWeight = counter.getTotalWeight();
		double f = getMarginalForgottenCount(counter);		
		double pdf = (f+totalPriorWeight) / (f + totalPriorWeight + specificWeight);
		assert MathUtils.isProb(pdf) && pdf > 0: pdf+" from forgotten:"+f+" tpw:"+totalPriorWeight+" specific:"+specificWeight+" in "+this;
		return pdf;
	}

	/**
	 * 
	 * @param sigPart e.g. "tag" Negative training is done vers us some other model. 
	 * This is the part of the signature which differs. If this isn't part of the signature, it means we're onto a generic
	 * model which should be left alone.
	 * @param context
	 * @param outcome
	 * @param done
	 * @param weight
	 */
	// TODO overly-specific defence
	void train1Not2(String sigPart, Cntxt context, X outcome, List<ICondDistribution<X,Cntxt>> done, double weight) 
	{
		if (weight==0) return;
		assert weight > 0;
		// Prevent double-training in the lower-level models
		if (done.contains(this))
			return;
		done.add(this);
		if (negCounts==null) return;
		assert trackedNegContexts > 0;
//		trainCnt++;
		
		// chop the context down to our size
		context = pareDown(context);
		assert context.bits.length == signature.length : context + " vs "
				+ signature;
		
		// up the counts
		AFiniteDistribution<X> counter = getNegMarginal(context);
		counter.addProb(outcome, weight);		
		
		// train the simpler models
		
//		TODO does this make sense here?? double passDownFactor = passDownFactor(counter);
//		double passDownWeight = weight * passDownFactor;
		
		for (int i = 0; i < simples.length; i++) {
			ICondDistribution<X, Cntxt> sm = simples[i];
			if (sm instanceof WWModel) {
				WWModel swm = ((WWModel)sm);
				// negative training affects generic models less
				double sweight = weight;
				if ( ! Containers.contains(sigPart, swm.getContextSignature())) {
					sweight = sweight * GENERIC_GETS_SOME_NEGATIVE_TRAINING;
				}
				swm.train1Not2(sigPart, context, outcome, done, sweight);				
			} 
			// ignore other model-types
		}		
	}

	/**
	 * Share prior weight between simpler models.
	 * 
	 * @param model
	 * @return the weight of this model. units: total (pseudo)counts. E.g. if
	 *         the model has a 10k vocab, then returning a weight of 20k would
	 *         have the effect of making the average score for a word count the
	 *         same as 2 actual-counts in this model.
	 */
	void updateSimpleWeightsNorm() {
		assert simplesWeights.length > 0;
		assert simplesWeights.length == simplesWeightsNorm.length;
		// share the total prior weight based on how well the models are doing
		double total = 0;
		for (int i = 0; i < simplesWeights.length; i++) {
			double stats = simplesWeights[i];			
			// Is it a tag-model? Boost it's influence over the generic word models 
			if (tagBoost!=0 && isTagged(simples[i])) {
				stats *= tagBoost;
			}
			total += stats;
		}
		final double priorWeight = getTotalPriorWeight();
		assert priorWeight != 0;
		// corner case: complete failure to model training data - just share
		// evenly
		if (total == 0) {
			for (int i = 0; i < simplesWeights.length; i++) {
				simplesWeightsNorm[i] = priorWeight / simplesWeights.length;
			}
			return;
		}
		for (int i = 0; i < simplesWeights.length; i++) {
			double strength = simplesWeights[i] / total;
			// Is it a tag-model? Boost it's influence over the generic word models 
			if (tagBoost!=0 && isTagged(simples[i])) {
				strength *= tagBoost;
			}
			simplesWeightsNorm[i] = strength * priorWeight;
		}
		assert MathUtils.equalish(MathUtils.sum(simplesWeightsNorm),
				priorWeight) : MathUtils.sum(simplesWeightsNorm) + " != "
				+ priorWeight;
	}

	/**
	 * For debugging really
	 * @param model
	 * @return the simpler model = this many examples 
	 */
	double getSimplerModelWeight(ICondDistribution<X, Cntxt> model) {
		int i = Containers.indexOf(model, simples);
		assert i != -1 : model;
		return simplesWeightsNorm[i];
	}

	/**
	 * @return a graph-view of the model.
	 * Edge-weights are normalised to [0,1].
	 */
	
	public IGraph.Directed<ICondDistribution<X, Cntxt>> getGraph() {
		DiGraph<ICondDistribution<X, Cntxt>> g = new DiGraph<ICondDistribution<X, Cntxt>>();
		getGraph2(g);
		return g.getValueAdaptor();
	}

	private void getGraph2(DiGraph<ICondDistribution<X, Cntxt>> g) {
		// Our node
		DiNode<ICondDistribution<X, Cntxt>> node = g.getNode(this);
		if (node==null) {
			node = g.addNode(this);
		}		
		
		// links to simpler models
		for(ICondDistribution<X, Cntxt> m : getSimplerModels()) {
			assert m != null : Printer.toString(getSimplerModels());
			// their node
			DiNode<ICondDistribution<X, Cntxt>> subNode = g.getNode(m);
			if (subNode==null) {
				subNode = g.addNode(m);
			}
			// Have edge-weights in the 0-1 bracket
			double w = getSimplerModelWeight(m) / getTotalPriorWeight();
			// does the edge exist?
			DiEdge<ICondDistribution<X, Cntxt>> edge = g.getEdge(node, subNode);
			if (edge != null) {
				assert MathUtils.equalish(w, edge.getWeight()) : w+" vs "+edge.getWeight();
				continue;
			}
			edge = g.addEdge(node, subNode, null);
			edge.setWeight(w);		
			assert subNode.getValue() != null : m;
			assert edge.getStart().getValue() != null;
			assert edge.getEnd().getValue() != null : edge+" "+subNode+" "+m;
		}
		
		// merge in sub-node models
		for(ICondDistribution<X, Cntxt> m : getSimplerModels()) {
			if (m instanceof WWModel) {
				IGraph.Directed<WWModel> subG = ((WWModel) m).getGraph();
				g.addAll(subG);				
			} else {
				// ??
			}
		}
		
	}


	@Deprecated // doesn't adjust the counts map -- only use in testing
	void setTracking(int[] tracked) {
		assert tracked.length == 4;
		this.trackedContexts = tracked[0];
		this.trackedOutcomes = tracked[1];
		this.trackedNegContexts = tracked[2];
		this.trackedNegOutcomes = tracked[3];
		// check the numbers are half sensible
		assert trackedContexts > 0 : this;
		assert trackedOutcomes > 1 : this;
		assert trackedNegOutcomes==0 || trackedNegOutcomes > 1 : trackedNegOutcomes+" "+this;
		if (desc!=null) {
			desc.put(DESC_TRACKING, tracked);
		}
	}		
	
	/**
	 * How much does this model try to remember?
	 * @return [trackedContexts,
				trackedOutcomes,
				trackedNegContexts,
				trackedNegOutcomes]
	 */
	public int[] getTracking() {
		return new int[]{
				this.trackedContexts,
				this.trackedOutcomes,
				this.trackedNegContexts,
				this.trackedNegOutcomes
		};
	}
	
	/**
	 * Marginalise (crudely) to give an unconditional version of this model. 
	 * HACK for understanding the model a bit more. These can be compared against other
	 * models easier than conditional ones can.<br>
	 * 
	 * WARNING: does NOT contain the sub-models!
	 * 
	 * @return a new model which can be modified without affecting this.
	 */
	AFiniteDistribution<X> getUnconditional() {
		if (counts.isEmpty()) {
			// nothing!
			return new ObjectDistribution();
		}
		// handle fixed features & no-context models
		if (counts.size() == 1) {
			AFiniteDistribution<X> marginal = Containers.first(counts.values());			
			ObjectDistribution<X> distro = new ObjectDistribution<X>(marginal.asMap());
			distro.normalise();
			return distro;
		}		
		// multi-context fun -- use HalfLifeMap's decay weights as crude probability weights
		HalfLifeMap<Cntxt, AFiniteDistribution<X>> hlm = (HalfLifeMap) counts;
		ObjectDistribution<X> distro = new ObjectDistribution<X>();
		Cntxt[] contexts = hlm.keySet().toArray(new Cntxt[0]); // in case of concurrent mods
		for(Cntxt c : contexts) {
			double wt = hlm.getCount(c);
			AFiniteDistribution<X> marginal = hlm.get(c);
			if (marginal==null) continue;
			distro.addAll(wt, marginal);
		}
		distro.normalise();
		return distro;
	}

	/**
	 * Replace uses of simple in this model & all sub-models. Uses == to test for a match.
	 * @param simple Must not be this model itself.
	 * @param replacementSimple
	 */
	void replaceModel(ICondDistribution simple, ICondDistribution replacementSimple) 
	{		
		for(int i=0; i<simples.length; i++) {
			if (simples[i]==simple) {
				checkSignature(replacementSimple);
				simples[i] = replacementSimple;
				continue;
			}
			if (simples[i] instanceof WWModel) {
				// recurse?
				WWModel wwmi = (WWModel) simples[i];
				//...check the signature (don't bother with irrelevant branches)				
				if (simple instanceof WWModel) {
					String[] sigi = wwmi.getContextSignature();
					String[] sigsim = ((WWModel) simple).getContextSignature();
					if ( ! StrUtils.isSubset(sigsim, sigi)) {
						continue;
					}
				}
				// ...yes, recurse
				wwmi.replaceModel(simple, replacementSimple);
			}
		}
	}

	/**
	 * Simpler models should have smaller signatures (otherwise we can't supply the right
	 * context by paring down).
	 * This checks that.
	 * @param replacementSimple
	 * @throws IllegalArgumentException
	 */
	private void checkSignature(ICondDistribution replacementSimple) throws IllegalArgumentException {
		if (replacementSimple instanceof WWModel) {
			WWModel rs = (WWModel) replacementSimple;
			String[] replaceSig = rs.getContextSignature();
			if ( ! StrUtils.isSubset(replaceSig, signature)) {
				throw new IllegalArgumentException(
					"Simpler signature is bigger: '"+Printer.toString(replaceSig)+"' in "+replacementSimple+" in "+this.getDesc());
			}
		}
	}

//	/**
//	 * Convenience for using with Depot. Fetch stored version of this and sub-models
//	 * from the Depot, swapping in the depot version for the blank one.
//	 * @param depot
//	 * @return version of this to use (from depot if already stored, otherwise this). Never null.
//	 * Also sync's the sub-models.
//	 */
//	public WWModel<X> sync(Depot depot) {
//		WWModel<X> old;
//		if (getDesc().isReadOnly()) {
//			old = depot.get(getDesc()); // no put
//		} else {
//			old = depot.putIfAbsent(getDesc(), this);			
//		}
//		// If we just loaded an existing model -- best use that then
//		WWModel<X> syncd = old==null? this : old;
////		if (old!=null) {
//			// TODO I think we don't have to sync the sub-models for an old model.		
////			return old;
////		}
//		
//		// sync the sub models
//		for(ICondDistribution simple : syncd.getAllSimplerModels()) {
//			Desc<ICondDistribution> sd = Desc.desc(simple);
//			if (sd==null) continue;
//			ICondDistribution useMe;
//			if (simple instanceof WWModel) {
//				useMe = ((WWModel) simple).sync(depot);
//			} else {
//				useMe = depot.putIfAbsent(sd, simple);
//			}
//			if (simple == useMe || useMe==null) continue;
//			// BUGGER: we want to use the old version :(
//			syncd.replaceModel(simple, useMe);			
//		}
//		
//		return syncd;
//	}

	/**
	 * For debugging use only
	 * @return Can be empty, never null
	 */
	@Deprecated
	public Map<Cntxt, AFiniteDistribution<X>> getNegContexts() {
		return negCounts==null? Collections.EMPTY_MAP : negCounts;
	}
	
	/**
	 * @param negEffect Small but > 1, e.g. 1.1
	 * 
	 * NB: Use {@link #trackedNegContexts} = 0 to switch off negative modelling.
	 */
	public void setNegEffect(double negEffect) {
		assert negEffect > 1.0; // Otherwise it has little effect 
		if (this.negEffect==negEffect) return;
		this.negEffect = negEffect;
		getDesc().put("nge", negEffect);
	}

}

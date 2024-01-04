package com.winterwell.maths.stats.distributions.cond;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.winterwell.maths.datastorage.HalfLifeMap;
import com.winterwell.maths.stats.distributions.discrete.AFiniteDistribution;
import com.winterwell.utils.Key;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.containers.TopNList;
import com.winterwell.utils.log.Log;

/**
 * TODO Try to describe a tagged WWModel, by comparing it to it's untagged sub-model
 * @testedby  WWModelDescriptionTest}
 * @author daniel
 *
 */
public class WWModelDescription<Tkn> {

//	FIXME use in model servlet or classifier servlet 
	
	WWModel<Tkn> model;
	private String tag;
	private int numTerms;
	
	public WWModelDescription(WWModel<Tkn> model, int numTerms) {
		this.model = model;
		this.numTerms = numTerms;
		tag = model.getDesc().get(new Key<String>("tag"));
	}
	
	@Override
	public String toString() {
		List<Tkn> topTerms;
		if (tag!=null /* NB: sub-models in the author-model may be about a tag, but not contain it in their signature */
				&& Containers.contains("tag", model.getContextSignature())) 
		{
			assert model.getDesc().containsKey("tag") : model+" "+model.getDesc();
			topTerms = topTerms_tagged();
			if (topTerms.isEmpty()) {
				topTerms = topTerms_untagged();
				topTerms.add(0, newTkn("(no-distinguishers)"));
			}
		} else {
			topTerms = topTerms_untagged();
			if (topTerms.isEmpty()) {
				return "training="+model.getTrainingCnt();
			}
		}
						
		return StrUtils.join(topTerms, ", ");
	}

	private Tkn newTkn(String string) {
		// TODO Auto-generated method stub
		throw new TodoException(); // new Tkn()
	}

	private List<Tkn> topTerms_untagged() {
		AFiniteDistribution<Tkn> marginal = model.getUnconditional();
		return marginal.getMostLikely(numTerms);
	}

	List<Tkn> topTerms_tagged() {
		assert Containers.contains("tag", model.signature) : model.signature+" "+model;
		AFiniteDistribution<Tkn> marginal = model.getUnconditional();
		ICondDistribution<Tkn, Cntxt>[] simples = model.getSimplerModels();
		WWModel submodel = null;
		for (ICondDistribution<Tkn, Cntxt> simple : simples) {
			if (simple instanceof WWModel) {
//				does it fit?
				WWModel wwm = (WWModel) simple;
				if (Containers.contains("tag", wwm.signature)) continue;
				// This hasn't got tag -- it's probably the model we want for comparison
				submodel = wwm;
				break;
			}
		}
		if (submodel==null) {
			Log.e("ai", "Wot no untagged submodel? "+model);
			return Arrays.asList(newTkn("?"));
		}
		AFiniteDistribution<Tkn> submarginal = submodel.getUnconditional();
		Map<String, AFiniteDistribution<Tkn>> models = new ArrayMap(
			tag, marginal,
			"", submarginal
				);
		
		Map<String, List<Tkn>> topTerms = AFiniteDistribution.getTopDistinguishingTerms(models, numTerms);
		return topTerms.get(tag);
	}
	
	public Collection<Cntxt> getTopContexts() {
		Cntxt[] contexts = model.counts.keySet().toArray(new Cntxt[0]); // in case of concurrent mods
		// handle fixed features & no-context models
		if (contexts.length <= numTerms) {
			return Arrays.asList(contexts);
		}		
		// multi-context fun -- use HalfLifeMap's decay weights as crude probability weights
		TopNList<Cntxt> topN = new TopNList<Cntxt>(numTerms);
		HalfLifeMap<Cntxt, AFiniteDistribution> hlm = (HalfLifeMap) model.counts;		
		for(Cntxt c : contexts) {
			double wt = hlm.getCount(c);
			topN.maybeAdd(c, wt);
		}
		return topN;
	}

	public String getTopContextsString() {
		Collection<Cntxt> contexts = getTopContexts();
		StringBuilder sb = new StringBuilder();
		for (Cntxt cntxt : contexts) {
			sb.append("[");
			String[] sig = cntxt.getSignature();
			Object[] bits = cntxt.getBits();
			for(int i=0; i<sig.length; i++) {
				if (bits[i] instanceof Integer && ((Integer)bits[i]) == 1) continue; // assume it's a fixed feature
				sb.append(sig[i]+":"+bits[i]);
				sb.append(", ");
			}
			if (sb.length() > 2) StrUtils.pop(sb, 2);
			sb.append("], ");
		}
		if ( ! contexts.isEmpty()) StrUtils.pop(sb, 1);
		sb.append(" of "+model.getContexts().size());
		return sb.toString();
	}
	
}

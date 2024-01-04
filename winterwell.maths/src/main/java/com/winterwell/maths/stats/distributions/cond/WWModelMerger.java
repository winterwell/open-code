//package com.winterwell.maths.stats.distributions.cond;
//
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.Map.Entry;
//
//import com.winterwell.maths.datastorage.HalfLifeMap;
//import com.winterwell.maths.stats.distributions.discrete.AFiniteDistribution;
//import com.winterwell.maths.stats.distributions.discrete.ObjectDistribution;
//import com.winterwell.utils.TodoException;
//import com.winterwell.utils.containers.ArrayMap;
//import com.winterwell.utils.containers.Containers;
//import com.winterwell.utils.log.Log;
//
//import com.winterwell.depot.Desc;
//import com.winterwell.depot.merge.AMerger;
//import com.winterwell.depot.merge.ClassMap;
//import com.winterwell.depot.merge.Diff;
//import com.winterwell.depot.merge.IMerger;
//import com.winterwell.depot.merge.MapMerger;
//import com.winterwell.utils.ReflectionUtils;
//
///**
// * @testedby  WWModelMergerTest}
// * @author daniel
// *
// */
//public class WWModelMerger extends AMerger<WWModel> {
//	
//	public WWModelMerger() {
//		super();
//		// map and list mergers
//		initStdMergers();
//		// keep the HLEntry counts
//		addMerge(HalfLifeMap.class, new HalfLifeMapMerger(mergers));
//		addMerge(HalfLifeMap.HLEntry.class, new HLEMerger(mergers));
//		// Distributions
//		addMerge(ObjectDistribution.class, new ObjectDistroMerger(mergers));
//	}
//	
//	@Override
//	public Diff diff(WWModel before, WWModel after) {
//		// The learned bits are counts, negCounts and the sub-model weightings
//		assert before.counts!=null : before;
//		IMerger mapMerger = getMerger(before.counts.getClass());	
//		assert mapMerger!=null;
//		Diff diffCounts = mapMerger.diff(before.counts, after.counts);
//		Diff diffNegCounts = mapMerger.diff(before.negCounts, after.negCounts);
//		
//		IMerger listMerger = getMerger(List.class);
//		Diff diffWeights = listMerger.diff(Containers.asList(before.simplesWeights), Containers.asList(after.simplesWeights));
//
//		// Recurse? No -- we rely on the modular convertor to handle the sub-models separately
//		
//		if (diffCounts==null && diffNegCounts==null && diffWeights==null) {
//			return null;
//		}
//		
//		return new Diff(getClass(),
//				new WWModelDiff(diffCounts, diffNegCounts, diffWeights));
//	}
//
//	@Override
//	public WWModel stripDiffs(WWModel v) {
//		// Should not arise
//		Log.e("WWModel", "StripDiffs?! "+v);
//		return v;
//	}
//	
//	@Override
//	public WWModel applyDiff(WWModel a, Diff _diff) {
//		if (_diff==null) return a;
//		WWModelDiff diff = (WWModelDiff) _diff.diff;
//		
//		// diff the counts
//		Map counts2 = applySubDiff(a.counts, diff.diffCounts);
//		if (counts2 != a.counts) {
//			ReflectionUtils.setPrivateField(a, "counts", counts2);
//		}
//		
//		Map ncounts2 = applySubDiff(a.negCounts, diff.diffNegCounts);
//		if (ncounts2 != a.negCounts) {
//			ReflectionUtils.setPrivateField(a, "negCounts", ncounts2);
//		}
//				
//		// Apply the weighting diff
//		if (diff.diffWeights!=null) {
//			List<Diff> dw = (List) diff.diffWeights.diff;
//			assert a.simplesWeights.length == dw.size();
//			double[] undiffed = Arrays.copyOf(a.simplesWeights, a.simplesWeights.length);		
//			for(int i=0; i<a.simplesWeights.length; i++) {
//				Diff dwi = dw.get(i);
//				if (dwi==null) continue;
//				a.simplesWeights[i] += (Double)dwi.diff;
//			}
//			a.updateSimpleWeightsNorm();
//		}
//		
//		return a;
//	}
//
//}
//
//class WWModelDiff {
//
//	public String toString() {
//		return "WWModelDiff["+diffCounts+"]";
//	}
//	
//	Diff diffCounts;
//	Diff diffNegCounts;
//	Diff diffWeights;
//
//	public WWModelDiff(Diff diffCounts, Diff diffNegCounts,
//			Diff diffWeights) {
//		this.diffCounts = diffCounts;
//		this.diffNegCounts = diffNegCounts;
//		this.diffWeights = diffWeights;
//	}
//	
//}
//
//
//class HLEMerger extends AMerger<HalfLifeMap.HLEntry> {
//
//	public HLEMerger(ClassMap<IMerger> mergers) {
//		super(mergers);
//	}
//	@Override
//	public Diff diff(HLEntry before, HLEntry after) {
//		assert before.getKey().equals(after.getKey());
//		Object bv = before.getValue();
//		double bc = before.getCount();
//		
//		Object av = after.getValue();
//		double ac = after.getCount();
//
//		if (av.equals(av) && ac==bc) {
//			return null;
//		}
//		
//		Object dv;		
////		} else {
//			IMerger m = getMerger(av.getClass());
//			if (m == null) {
//				dv = av;
//			} else {
//				dv = m.diff(bv, av);
//			}
////		}
//		
//		double dc = ac - bc;
//		
//		return new Diff(getClass(), Arrays.asList(dv, dc));
//	}
//
//	@Override
//	public HLEntry applyDiff(HLEntry a, Diff diff) {
//		List _diff = (List) diff.diff;
//		Object dv = _diff.get(0);
//		if (dv==null) {
//			// no op
//		} else if (dv instanceof Diff) {
//			Object v2 = applySubDiff(a.getValue(), (Diff)dv);
//			a.setValue(v2);
//		} else {
//			a.setValue(dv);
//		}
//		a.setCount(a.getCount() + (Double)_diff.get(1));
//		return a;
//	}
//	@Override
//	public HLEntry stripDiffs(HLEntry v) {
//		// not recursive
//		return v;
//	}
//	
//}
//
//
//class ObjectDistroMerger extends AMerger<ObjectDistribution> {
//
//	public ObjectDistroMerger(ClassMap<IMerger> mergers) {
//		super(mergers);
//	}
//
//	@Override
//	public Diff diff(ObjectDistribution before, ObjectDistribution after) {
//		assert before.getPseudoCount()==0;
//		assert after.getPseudoCount()==0;
//		Map bmap = before.asMap();
//		Map amap = after.asMap();
//		IMerger m = getMerger(bmap.getClass());
//		Object diff = m.diff(bmap, amap);
//		return new Diff(getClass(), diff);
//	}
//
//	@Override
//	public ObjectDistribution applyDiff(ObjectDistribution a, Diff diff) {
//		assert a.getPseudoCount()==0;
//		Object am2 = applySubDiff(a.asMap(), (Diff)diff.diff);
//		assert am2==a.asMap();
//		return a;
//	}
//	
//	@Override @Deprecated // should not arise
//	public ObjectDistribution stripDiffs(ObjectDistribution v) {
//		ObjectDistribution od = new ObjectDistribution();
//		ObjectDistribution od2 = applyDiff(od, new Diff(getClass(), v));
//		return od2;
//	}
//	
//}
//
//class HalfLifeMapMerger extends AMerger<HalfLifeMap> {
//
//	public HalfLifeMapMerger(ClassMap<IMerger> mergers) {
//		super(mergers);
//	}
//
//	@Override
//	public Diff diff(HalfLifeMap before, HalfLifeMap after) {
//		Map bMap = before.getBaseMap();
//		Map aMap = after.getBaseMap();
//		assert ! (bMap instanceof HalfLifeMap) : bMap.getClass()+" "+before;
//		IMerger m = getMerger(bMap.getClass());
//		Object diff = m.diff(bMap, aMap);
//		if (diff==null) return null;
//		return new Diff(getClass(), diff);
//	}
//
//	@Override
//	public HalfLifeMap applyDiff(HalfLifeMap a, Diff diff) {
//		Map aMap = a.getBaseMap();
//		assert ! (aMap instanceof HalfLifeMap) : aMap.getClass()+" "+a;
//		IMerger m = getMerger(aMap.getClass());
//		Object aMap2 = m.applyDiff(aMap, (Diff) diff.diff);
//		assert aMap2==aMap;
//		return a;
//	}
//	
//	@Override
//	public HalfLifeMap stripDiffs(HalfLifeMap v) {
//		Map aMap = v.getBaseMap();
//		IMerger m = getMerger(aMap.getClass());
//		Map aMap2 = (Map) m.stripDiffs(aMap);
//		if (aMap==aMap2) return v;
////		HalfLifeMap clean = new HalfLifeMap(v.getIdealSize());
////		clean.putAll(aMap2); This would break if we switch to inflationary units, so I think the edit-in-place is slightly less likely to cause bugs. ^Dan
//		aMap.clear(); aMap.putAll(aMap2);
//		return v;
//	}
//	
//}
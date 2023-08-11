package com.winterwell.optimization.genetic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.winterwell.maths.stats.distributions.GaussianBall;
import com.winterwell.maths.stats.distributions.d1.UniformDistribution1D;
import com.winterwell.maths.vector.X;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.TodoException;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.Range;

/**
 * Use with {@link MutateMe} annotated classes.
 * @author daniel
 *
 * @param <T> Type of the object being mutated (typically a config object) 
 */
public class MutateMeBreeder<T> implements IBreeder<T> {

	private T original;
	private List<Field> fields;

	public MutateMeBreeder(T original) {
		this.original = original;
		fields = ReflectionUtils.getAnnotatedFields(original.getClass(), MutateMe.class, true);
		mutation = Math.pow(0.75, fields.size());
	}
	
	@Override
	public T generate() {
		return original;
	}

	/**
	 * Probability for each field of mutating
	 */
	private double mutation;

	@Override
	public T mutate(T candidate) {
		T b = Utils.copy(candidate);
		for(int i=0,n=fields.size(); i<n; i++) {
			if ( ! Utils.getRandomChoice(mutation)) continue;
			try {
				Field f = fields.get(i);
				Object v = f.get(candidate);
				Object modv = mutate(f, v);
				f.set(b, modv);
			} catch (Exception e) {
				throw Utils.runtime(e);
			}
		}
		return b;
	}

	VectorGAOp vectorOp = new VectorGAOp(new GaussianBall(new X(0), 1));
	
	public Object mutate(Field f, Object value) {
		Class<?> type = f.getType();
		// Boolean?
		if (type == Boolean.class || type == boolean.class) {
			return random.nextBoolean();
		}
 
		// Set?
		MutateMe mm = f.getAnnotation(MutateMe.class);
		if ( ! mm.choices().isEmpty()) {
			if (type == String.class) {
				List<String> options = StrUtils.split(mm.choices());
				// TODO deserialise for other types
				return Utils.getRandomMember(options);
			}else if (type == List.class) {
				List<String> options = StrUtils.split(mm.choices());
				Set<String> mutatedSet = (Set<String>) Utils.getRandomSelection(Utils.getRandom().nextInt(options.size())+1, options);
				List<String> list = new ArrayList<String>();
				list.addAll(mutatedSet);
				return list;
			}
		}
		
		// Number?
		if (ReflectionUtils.isaNumber(type)) {
			// Initialise with a uniform distribution across the range
			// Then mutate with a gaussian distribution.
			// ??This allows tuning across scales -- but maybe not always best??
			double mod;
			Range range = new Range(mm.high(), mm.low());
			
			// always sample from a uniform distribution
			mod = new UniformDistribution1D(range).sample();
			
			/*
			 * old code that would mutate to a value close to the original using vectorOp
			 */
//			if (value==null) {
//				mod = new UniformDistribution1D(range).sample();
//			} else {
//				double x = ((Number)value).doubleValue();
          // NB: the mutation size is relative to the size of x 
//				mod = vectorOp.mutate2(x);
//				if (range.size() != 0) {
//					mod = range.cap(mod);
//				}
//			}

			if (type == Integer.class || type==int.class) {
				return (int) Math.round(mod);
			}
			if (type == Long.class || type==long.class) {
				return (long) Math.round(mod);
			}
			if (type == Float.class || type==float.class) {
				return (float) mod;
			}
			return mod;
		}
		
		throw new TodoException(type+" "+value);
	}

	Random random = Utils.getRandom();
	
	@Override
	public T crossover(T a, T b) {		
		if (fields.size()<2) {
			// Too small to crossover
			return random.nextBoolean()? a : b;
		}
		int switchDim = 1+Utils.getRandom().nextInt(fields.size()-1);
		T c = Utils.copy(a);
		for(int i=switchDim,n=fields.size(); i<n; i++) {
			try {
				Field f = fields.get(i);
				Object bv = f.get(b);
				f.set(c, bv);
			} catch (Exception e) {
				throw Utils.runtime(e);
			}
		}
		return c;
	}

	@Override
	public void setRandomSource(Random seed) {
		random = seed;
	}

	public void mutateOriginal() {
		this.original = mutate(original);		
	}
	
	public void setMutation(double m) {
		this.mutation = m;
	}

}

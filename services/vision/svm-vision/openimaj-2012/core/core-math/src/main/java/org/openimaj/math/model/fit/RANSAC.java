/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.math.model.fit;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.procedure.TIntProcedure;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.openimaj.data.RandomData;
import org.openimaj.math.model.Model;
import org.openimaj.util.pair.IndependentPair;


/**
 * The RANSAC Algorithm	(RANdom SAmple Consensus)
 * 
 * For fitting noisy data consisting of inliers and outliers
 * to a model.
 * 
 * Assume:
 * 		M data items required to estimate parameter x
 *		N data items in total
 *
 *	1.) select M data items at random
 *	2.) estimate parameter x
 * 	3.) find how many of the N data items fit x within
 *		tolerence tol, call this K
 *	4.) if K is large enough (bigger than numItems) accept x and exit with success
 * 	5.) repeat 1..4 nIter times
 * 	6.) fail - no good x fit of data
 * 
 * In this implementation, the conditions that control the iterations are configurable.
 * In addition, the best matching model is always stored, even if the fitData() method
 * returns false.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 *
 * @param <I> type of independent data
 * @param <D> type of dependent data
 */
public class RANSAC<I, D> implements RobustModelFitting<I, D> {	
	/**
	 * Interface for classes that can control RANSAC iterations
	 */
	public static interface StoppingCondition {
		/**
		 * Initialise the stopping condition if necessary. Return false
		 * if the initialisation cannot be performed and RANSAC should fail
		 * @param data The data being fitted
		 * @param model The model to fit
		 * @return true if initialisation is successful, false otherwise.
		 */
		public abstract boolean init(final List<?> data, Model<?,?> model);

		/**
		 * Should we stop iterating and return the model?
		 * @param numInliers number of inliers in this iteration
		 * @return true if the model is good and iterations should stop
		 */
		public abstract boolean shouldStopIterations(int numInliers);

		/**
		 * Should the model be considered to fit after the final iteration
		 * has passed?
		 * @param numInliers number of inliers in the final model
		 * @return true if the model fits, false otherwise
		 */
		public abstract boolean finalFitCondition(int numInliers);
	}

	/**
	 * Stopping condition that tests the number of matches against
	 * a threshold. If the number exceeds the threshold, then the model
	 * is considered to fit. 
	 */
	public static class NumberInliersStoppingCondition implements StoppingCondition {
		int limit;

		/**
		 * Construct the stopping condition with the given threshold
		 * on the number of data points which must match for a model
		 * to be considered a fit.
		 * @param limit the threshold
		 */
		public NumberInliersStoppingCondition(int limit) {
			this.limit = limit;
		}

		@Override
		public boolean init(List<?> data, Model<?,?> model) {
			if (limit < model.numItemsToEstimate()) {
				limit = model.numItemsToEstimate();
			}

			if (data.size() < limit)
				return false;
			return true;
		}

		@Override
		public boolean shouldStopIterations(int numInliers) {
			return numInliers >= limit; //stop if there are more inliers than our limit
		}

		@Override
		public boolean finalFitCondition(int numInliers) {
			return numInliers >= limit;
		}
	}

	/**
	 * Stopping condition that tests the number of matches against
	 * a percentage threshold of the whole data. If the number exceeds 
	 * the threshold, then the model is considered to fit. 
	 */
	public static class PercentageInliersStoppingCondition extends NumberInliersStoppingCondition {
		double percentageLimit;

		/**
		 * Construct the stopping condition with the given percentage threshold
		 * on the number of data points which must match for a model
		 * to be considered a fit.
		 * @param percentageLimit the percentage threshold
		 */
		public PercentageInliersStoppingCondition(double percentageLimit) {
			super(0);
			this.percentageLimit = percentageLimit;
		}

		@Override
		public boolean init(List<?> data, Model<?,?> model) {
			this.limit = (int)Math.rint(percentageLimit * data.size());
			return super.init(data, model);
		}
	}

	/**
	 * Stopping condition that tests the number of matches against
	 * a percentage threshold of the whole data. If the number exceeds 
	 * the threshold, then the model is considered to fit. 
	 */
	public static class ProbabilisticMinInliersStoppingCondition implements StoppingCondition {
		private static final double DEFAULT_INLIER_IS_BAD_PROBABILITY = 0.1;
		private static final double DEFAULT_PERCENTAGE_INLIERS = 0.25;
		private double inlierIsBadProbability;
		private double desiredErrorProbability;
		private double percentageInliers;

		private int numItemsToEstimate;
		private int iteration = 0;
		private int limit;
		private int maxInliers = 0;
		private double currentProb;
		private int numDataItems;

		/**
		 * Default constructor.
		 * @param desiredErrorProbability The desired error rate
		 * @param inlierIsBadProbability The probability an inlier is bad
		 * @param percentageInliers The percentage of inliers in the data
		 */
		public ProbabilisticMinInliersStoppingCondition(double desiredErrorProbability, double inlierIsBadProbability, double percentageInliers) {
			this.desiredErrorProbability = desiredErrorProbability;
			this.inlierIsBadProbability = inlierIsBadProbability;
			this.percentageInliers = percentageInliers;
		}

		/**
		 * Constructor with defaults for bad inlier probability and percentage inliers.
		 * @param desiredErrorProbability The desired error rate
		 */
		public ProbabilisticMinInliersStoppingCondition(double desiredErrorProbability) {
			this(desiredErrorProbability, DEFAULT_INLIER_IS_BAD_PROBABILITY, DEFAULT_PERCENTAGE_INLIERS);
		}

		@Override
		public boolean init(List<?> data, Model<?,?> model) {
			numItemsToEstimate = model.numItemsToEstimate();
			numDataItems = data.size();
			this.limit = calculateMinInliers();
			this.iteration = 0;
			this.currentProb = 1.0;
			this.maxInliers = 0;

			return true;
		}

		@Override
		public boolean finalFitCondition(int numInliers) {
			return numInliers >= limit;
		}

		private int calculateMinInliers() {
			double pi, sum;
			int i, j;

			for( j = numItemsToEstimate+1; j <= numDataItems; j++ )
			{
				sum = 0;
				for( i = j; i <= numDataItems; i++ )
				{
					pi = (i-numItemsToEstimate) * Math.log( inlierIsBadProbability ) + (numDataItems-i+numItemsToEstimate) * Math.log( 1.0 - inlierIsBadProbability ) +
					log_factorial( numDataItems - numItemsToEstimate ) - log_factorial( i - numItemsToEstimate ) - log_factorial( numDataItems - i );
					/*
					 * Last three terms above are equivalent to log( n-m choose i-m )
					 */
					sum += Math.exp( pi );
				}
				if( sum < desiredErrorProbability )
					break;
			}
			return j;
		}

		private double log_factorial(int n) {
			double f = 0;
			int i;

			for( i = 1; i <= n; i++ )
				f += Math.log( i );

			return f;
		}

		@Override
		public boolean shouldStopIterations(int numInliers) {

			if( numInliers > maxInliers ) {
				maxInliers = numInliers;
				percentageInliers = (double)maxInliers / numDataItems;
				
//				System.err.format("Updated maxInliers: %d\n", maxInliers);
			}
			currentProb = Math.pow( 1.0 - Math.pow( percentageInliers, numItemsToEstimate ), ++iteration );
			return currentProb <= this.desiredErrorProbability;
		}
	}

	/**
	 * Stopping condition that allows the RANSAC algorithm to run until
	 * all the iterations have been exhausted. The fitData method will return
	 * true if there are at least as many inliers as datapoints required to estimate 
	 * the model, and the model will be the one from the iteration that had the
	 * most inliers. 
	 */
	public static class BestFitStoppingCondition implements StoppingCondition {
		int required;
		@Override
		public boolean init(List<?> data, Model<?,?> model) {
			required = model.numItemsToEstimate();
			return true;
		}

		@Override
		public boolean shouldStopIterations(int numInliers) {
			return false; //just iterate until the end
		}

		@Override
		public boolean finalFitCondition(int numInliers) {
			return numInliers > required; //accept the best result as a good fit if there are enough inliers 
		}
	}

	protected Model<I, D> model;
	protected int nIter;
	protected boolean improveEstimate;
	protected TIntArrayList inliers;
	protected TIntArrayList outliers;
	protected TIntArrayList bestModelInliers;
	protected TIntArrayList bestModelOutliers;
	protected StoppingCondition stoppingCondition;
	private List<? extends IndependentPair<I, D>> modelConstructionData;

	/**
	 * Create a RANSAC object
	 * @param model Model object with which to fit data
	 * @param nIterations Maximum number of allowed iterations (L)
	 * @param stoppingCondition the stopping condition
	 * @param impEst True if we want to perform a final fitting of the model with all inliers, false otherwise
	 */
	public RANSAC(Model<I, D> model, int nIterations, StoppingCondition stoppingCondition, boolean impEst)
	{
		this.stoppingCondition = stoppingCondition;
		this.model = model;
		nIter = nIterations;
		improveEstimate = impEst;

		inliers = new TIntArrayList();
		outliers = new TIntArrayList();
	}

	@Override
	public boolean fitData(final List<? extends IndependentPair<I, D>> data)
	{
		int l, M = model.numItemsToEstimate();

		bestModelInliers = null;
		bestModelOutliers = null;

		if (data.size() < M || !stoppingCondition.init(data, model)) {
			return false; //there are not enough points to create a model, or init failed
		}

		for (l=0; l<nIter; l++) {
			//1
			List<? extends IndependentPair<I,D>> rnd = getRandomItems(data, M);
			this.setModelConstructionData(rnd);
			//2
			model.estimate(rnd);

			//3
			int K=0, i=0;
			inliers.clear();
			outliers.clear();
			for (IndependentPair<I,D> dp : data) {
				if (model.validate(dp))
				{
					K++;
					inliers.add(i);
				} else {
					outliers.add(i);
				}
				i++;
			}

			if (bestModelInliers == null || inliers.size() >= bestModelInliers.size()) {
				bestModelInliers = new TIntArrayList(inliers);
				bestModelOutliers = new TIntArrayList(outliers);
			}
//			System.err.println(K);
			//4
			if (stoppingCondition.shouldStopIterations(K)) {
				//generate "best" fit from all the iterations
				inliers = bestModelInliers;
				outliers = bestModelOutliers;
				
				if (improveEstimate) {
//					System.err.format("Improving with %d inliers\n",inliers.size());
					//improve fit...
					final List<IndependentPair<I,D>> vdata = new LinkedList<IndependentPair<I,D>>();

					inliers.forEach(new TIntProcedure(){
						@Override
						public boolean execute(int value) {
							vdata.add(data.get(value));
							return true;
						}
					});

					if (inliers.size() >= model.numItemsToEstimate())
						model.estimate(vdata);
				}
				boolean stopping = stoppingCondition.finalFitCondition(inliers.size());
//				System.err.format("done: %b\n",stopping);
				return stopping;
			}
			//5
			//...repeat...
		}


		final List<IndependentPair<I,D>> vdata = new LinkedList<IndependentPair<I,D>>();

		//generate "best" fit from all the iterations
		inliers = bestModelInliers;
		outliers = bestModelOutliers;
		
		bestModelInliers.forEach(new TIntProcedure() {
			@Override
			public boolean execute(int value) {
				vdata.add(data.get(value));
				return true;
			}
		});

		if (vdata.size() >= M)
			model.estimate(vdata);

		//6 - fail
		return stoppingCondition.finalFitCondition(inliers.size());
	}

	protected List<? extends IndependentPair<I, D>> getRandomItems(List<? extends IndependentPair<I,D>> data, int nItems) {
		int [] rints = RandomData.getUniqueRandomInts(nItems, 0, data.size());
		List<IndependentPair<I,D>> out = new ArrayList<IndependentPair<I,D>>(nItems);

		for (int i : rints) {
			out.add(data.get(i));
		}

		return out;
	}

	@Override
	public TIntArrayList getInliers()
	{
		return inliers;
	}

	@Override
	public TIntArrayList getOutliers()
	{
		return outliers;
	}

	/**
	 * @return maximum number of allowed iterations
	 */
	public int getMaxIterations() {
		return nIter;
	}

	/**
	 * Set the maximum number of allowed iterations
	 * @param nIter maximum number of allowed iterations
	 */
	public void setMaxIterations(int nIter) {
		this.nIter = nIter;
	}

	@Override
	public Model<I, D> getModel() {
		return model;
	}

	/**
	 * Set the underlying model being fitted
	 * @param model the model
	 */
	public void setModel(Model<I, D> model) {
		this.model = model;
	}

	/**
	 * @return whether RANSAC should attempt to improve the model using all inliers as data
	 */
	public boolean isImproveEstimate() {
		return improveEstimate;
	}

	/**
	 * Set whether RANSAC should attempt to improve the model using all inliers as data
	 * @param improveEstimate should RANSAC attempt to improve the model using all inliers as data
	 */
	public void setImproveEstimate(boolean improveEstimate) {
		this.improveEstimate = improveEstimate;
	}

	/**
	 * Set the data used to construct the model 
	 * @param modelConstructionData
	 */
	public void setModelConstructionData(List<? extends IndependentPair<I, D>> modelConstructionData) {
		this.modelConstructionData = modelConstructionData;
	}

	/**
	 * @return The data used to construct the model.
	 */
	public List<? extends IndependentPair<I, D>> getModelConstructionData() {
		return modelConstructionData;
	}

	/**
	 * Get the best inliers from all iterations.
	 * @param data The data used in #fitData(List).
	 * @return the inliers.
	 */
	public List<? extends IndependentPair<I, D>> getBestInliers(final List<? extends IndependentPair<I, D>> data) {
		final List<IndependentPair<I,D>> vdata = new LinkedList<IndependentPair<I,D>>();

		inliers.forEach(new TIntProcedure(){
			@Override
			public boolean execute(int value) {
				vdata.add(data.get(value));
				return true;
			}
		});
		return vdata;
	}
}

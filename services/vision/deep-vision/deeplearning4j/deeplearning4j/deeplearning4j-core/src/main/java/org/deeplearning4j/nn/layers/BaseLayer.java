/*
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.layers;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.optimize.Solver;
import org.deeplearning4j.optimize.api.ConvexOptimizer;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.Dropout;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * A layer with a bias
 * and activation function
 * @author Adam Gibson
 */
public abstract class BaseLayer<LayerConfT extends org.deeplearning4j.nn.conf.layers.Layer>
        implements Layer {

    protected INDArray input;
    protected Map<String,INDArray> params;
    protected NeuralNetConfiguration conf;
    protected INDArray dropoutMask;
    protected ParamInitializer paramInitializer;
    protected double score = 0.0;
    protected ConvexOptimizer optimizer;
    protected Gradient gradient;
    protected Collection<IterationListener> iterationListeners = new ArrayList<>();
    protected int index = 0;
    protected int inputMiniBatchSize;

    public BaseLayer(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    public BaseLayer(NeuralNetConfiguration conf, INDArray input) {
        this.input = input;
        this.conf = conf;
    }

    protected LayerConfT layerConf() {
        return (LayerConfT) this.conf.getLayer();
    }

    public INDArray getInput() {
        return input;
    }

    public void setInput(INDArray input,boolean training) {
        if(conf.getLayer().getDropOut() > 0 && training)
            this.dropoutMask = Dropout.applyDropout(input,conf.getLayer().getDropOut(),dropoutMask);
        this.input = input;
    }

    @Override
    public void setInput(INDArray input) {
        setInput(input,true);
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public Collection<IterationListener> getListeners() {
        return iterationListeners;
    }

    @Override
    public void setListeners(Collection<IterationListener> listeners) {
        this.iterationListeners = listeners != null ? listeners : new ArrayList<IterationListener>();
    }

    @Override
    public void setListeners(IterationListener... listeners) {
        this.iterationListeners = new ArrayList<>();
        for(IterationListener l : listeners)
            iterationListeners.add(l);
    }

    @Override
    public Gradient error(INDArray errorSignal) {
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        Gradient nextLayerGradient = new DefaultGradient();
        INDArray wErrorSignal = errorSignal.mmul(W.transpose());
        nextLayerGradient.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY,wErrorSignal);
        return nextLayerGradient;
    }

    @Override
    public INDArray derivativeActivation(INDArray input) {
        INDArray deriv = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(conf().getLayer().getActivationFunction(), input).derivative());
        return deriv;
    }

    @Override
    public Gradient calcGradient(Gradient layerError, INDArray activation) {
        Gradient ret = new DefaultGradient();
        INDArray weightErrorSignal = layerError.getGradientFor(DefaultParamInitializer.WEIGHT_KEY);
        INDArray weightError = weightErrorSignal.transpose().mmul(activation).transpose();
        ret.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY,weightError);
        INDArray biasGradient = weightError.mean(0);
        ret.gradientForVariable().put(DefaultParamInitializer.BIAS_KEY,biasGradient);

        return ret;
    }

    @Override
    public Pair<Gradient,INDArray> backpropGradient(INDArray epsilon) {
        //If this layer is layer L, then epsilon is (w^(L+1)*(d^(L+1))^T) (or equivalent)
        INDArray z = preOutput(input);
        INDArray activationDerivative = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(conf().getLayer().getActivationFunction(), z).derivative());
        INDArray delta = epsilon.muli(activationDerivative);

        Gradient ret = new DefaultGradient();
        ret.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY, delta.transpose().mmul(input).transpose());
        ret.gradientForVariable().put(DefaultParamInitializer.BIAS_KEY, delta.sum(0));
        
        INDArray epsilonNext = params.get(DefaultParamInitializer.WEIGHT_KEY).mmul(delta.transpose()).transpose();

        return new Pair<>(ret,epsilonNext);
    }

    public void fit() {
        fit(this.input);
    }

    @Override
    public void computeGradientAndScore() {
        if (this.input == null)
            return;

        INDArray output = activate(true);
        setScoreWithZ(output);

    }


    protected void setScoreWithZ(INDArray z) {
    }


    @Override
    public INDArray preOutput(INDArray x, TrainingMode training) {
        return preOutput(x,training == TrainingMode.TRAIN);
    }

    @Override
    public INDArray activate(TrainingMode training) {
        return activate(training == TrainingMode.TRAIN);
    }

    @Override
    public INDArray activate(INDArray input, TrainingMode training) {
        return activate(input,training == TrainingMode.TRAIN);
    }

    /**
     * Objective function:  the specified objective
     * @return the score for the objective
     */

    @Override
    public double score() {
        return score;
    }

    @Override
    public Gradient gradient() {
        return gradient;
    }

    /**
     * iterate one iteration of the network
     *
     * @param input  the input to iterate on
     */
    @Override
    public void iterate(INDArray input) {
        setInput(input.dup());
        applyDropOutIfNecessary(this.input,true);
        Gradient gradient = gradient();
        for(String paramType : gradient.gradientForVariable().keySet()) {
            update(gradient.getGradientFor(paramType), paramType);
        }
    }

    @Override
    public void update(Gradient gradient) {
        for(String paramType : gradient.gradientForVariable().keySet()) {
            update(gradient.getGradientFor(paramType), paramType);
        }
    }

    @Override
    public void update(INDArray gradient, String paramType) {
		setParam(paramType, getParam(paramType).addi(gradient));
    }


    @Override
    public ConvexOptimizer getOptimizer() {
        if(optimizer == null) {
            Solver solver = new Solver.Builder()
                    .model(this).configure(conf())
                    .build();
            this.optimizer = solver.getOptimizer();
        }
        return optimizer;
    }

    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    /**Returns the parameters of the neural network as a flattened row vector
     * @return the parameters of the neural network
     */
    @Override
    public INDArray params() {
        return Nd4j.toFlattened('f',params.values());
    }

    @Override
    public INDArray getParam(String param) {
        return params.get(param);
    }

    @Override
    public void setParam(String key, INDArray val) {
        params.put(key, val);
    }

    @Override
    public void setParams(INDArray params) {
        List<String> parameterList = conf.variables();
        int length = 0;
        for(String s : parameterList)
            length += getParam(s).length();
        if(params.length() != length)
            throw new IllegalArgumentException("Unable to set parameters: must be of length " + length);
        int idx = 0;
        Set<String> paramKeySet = this.params.keySet();
        for(String s : paramKeySet) {
            INDArray param = getParam(s);
            INDArray get = params.get(NDArrayIndex.point(0),NDArrayIndex.interval(idx, idx + param.length()));
            if(param.length() != get.length())
                throw new IllegalStateException("Parameter " + s + " should have been of length " + param.length() + " but was " + get.length());
            setParam(s,get.reshape('f',param.shape()));
            idx += param.length();

        }

    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        this.params = paramTable;
    }

    @Override
    public void initParams() {
        paramInitializer.init(paramTable(), conf());
    }

    @Override
    public Map<String, INDArray> paramTable() {
        return params;
    }

    @Override
    public INDArray preOutput(INDArray x, boolean training) {
        if(x == null)
            throw new IllegalArgumentException("No null input allowed");

        setInput(x,training);
        applyDropOutIfNecessary(x,training);
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        if(conf.isUseDropConnect() && training) {
            if (conf.getLayer().getDropOut() > 0) {
                W = Dropout.applyDropConnect(this,DefaultParamInitializer.WEIGHT_KEY);
            }
        }

        INDArray ret = x.mmul(W).addiRowVector(b);
        return ret;
    }

    @Override
    public INDArray activate(boolean training) {
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        if(conf.isUseDropConnect() && training) {
            W = Dropout.applyDropConnect(this,DefaultParamInitializer.WEIGHT_KEY);
        }

        INDArray ret = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(conf.getLayer().getActivationFunction(), input().mmul(W).addiRowVector(b)));
        return ret;
    }

    @Override
    public  INDArray activate(INDArray input) {
        setInput(input, true);
        return activate(true);
    }

    @Override
    public INDArray activate(INDArray input, boolean training) {
        setInput(input, training);
        return activate(training);
    }

    @Override
    public  INDArray activate() {
        return activate(false);
    }


    /**
     * Classify input
     * @param x the input (can either be a matrix or vector)
     * If it's a matrix, each row is considered an example
     * and associated rows are classified accordingly.
     * Each row will be the likelihood of a label given that example
     * @return a probability distribution for each row
     */
    @Override
    public  INDArray preOutput(INDArray x) {
        return preOutput(x,true);
    }

    @Override
    public double calcL2() {
    	if(!conf.isUseRegularization() || conf.getL2() <= 0.0 ) return 0.0;
        return 0.5 * conf.getL2() * Transforms.pow(getParam(DefaultParamInitializer.WEIGHT_KEY),2).sum(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public double calcL1() {
    	if(!conf.isUseRegularization() || conf.getL1() <= 0.0 ) return 0.0;
        return conf.getL1() * Transforms.abs(getParam(DefaultParamInitializer.WEIGHT_KEY)).sum(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public int batchSize() {
        return input.size(0);
    }


    @Override
    public INDArray activationMean() {
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        return input().mmul(W).addiRowVector(b);
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }


    @Override
    public void clear() {
        if(input != null) {
            input.data().destroy();
            input = null;
        }
    }

    protected void applyDropOutIfNecessary(INDArray input,boolean training) {
        if(conf.getLayer().getDropOut() > 0 && !conf.isUseDropConnect() && training) {
            dropoutMask = Dropout.applyDropout(input,conf.getLayer().getDropOut(),dropoutMask);
        }
    }

    /**
     * Averages the given logistic regression from a mini batch into this layer
     * @param l the logistic regression layer to average into this layer
     * @param batchSize  the batch size
     */
    @Override
    public void merge(Layer l, int batchSize) {
        setParams(params().addi(l.params().divi(batchSize)));
        computeGradientAndScore();
    }

    @Override
    public Layer clone() {
        Layer layer = null;
        try {
            Constructor c = getClass().getConstructor(NeuralNetConfiguration.class);
            layer = (Layer) c.newInstance(conf);
            Map<String,INDArray> linkedTable = new LinkedHashMap<>();
            for(String s: params.keySet())
                linkedTable.put(s,params.get(s).dup());
            layer.setParamTable(linkedTable);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return layer;

    }

    @Override
    public Type type() {
        return Type.FEED_FORWARD;
    }

    /**
     * The number of parameters for the model
     *
     * @return the number of parameters for the model
     */
    @Override
    public int numParams() {
        int ret = 0;
        for(INDArray val : params.values())
            ret += val.length();
        return ret;
    }

    @Override
    public void fit(INDArray input) {
        if(input != null) {
            setInput(input.dup());
            applyDropOutIfNecessary(this.input,true);
        }
        Solver solver = new Solver.Builder()
                .model(this).configure(conf()).listeners(getListeners())
                .build();
        this.optimizer = solver.getOptimizer();
        solver.optimize();
    }


    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        return new Pair<>(gradient(),score());
    }

    @Override
    public INDArray input() {
        return input;
    }

    @Override
    public void validateInput() {

    }

    /**
     * Create a gradient list based on the passed in parameters.
     * Will throw an IllegalArgumentException if the number of gradient matrices
     * isn't equal to the number of keys in the parameter list
     * @param gradients the gradients to create from
     * @return the create based on the passed in ndarrays
     */
    protected Gradient createGradient(INDArray...gradients) {
        Gradient ret = new DefaultGradient();
        if(gradients.length != conf.variables().size())
            throw new IllegalArgumentException("Unable to create gradients...not equal to number of parameters");
        for(int i = 0; i < gradients.length; i++) {
            INDArray paramI = getParam(conf.variables().get(i));
            if(!Arrays.equals(paramI.shape(),gradients[i].shape()))
                throw new IllegalArgumentException("Gradient at index " + i + " had wrong gradient size of " + Arrays.toString(gradients[i].shape()) + " when should have been " + Arrays.toString(paramI.shape()));
            ret.gradientForVariable().put(conf.variables().get(i),gradients[i]);
        }
        return ret;
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "conf=" + conf +
                ", input=" + input +
                ", params=" + params +
                ", dropoutMask=" + dropoutMask +
                ", paramInitializer=" + paramInitializer +
                ", score=" + score +
                ", optimizer=" + optimizer +
                ", listeners=" + iterationListeners +
                '}';
    }

    @Override
    public Layer transpose() {
        if(!(conf.getLayer() instanceof org.deeplearning4j.nn.conf.layers.FeedForwardLayer))
            throw new UnsupportedOperationException("unsupported layer type: " + conf.getLayer().getClass().getName());

        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        Layer layer;
        try {
            Constructor c = getClass().getConstructor(NeuralNetConfiguration.class, INDArray.class, INDArray.class, INDArray.class);
            NeuralNetConfiguration clone = conf.clone();  // assume a deep clone here

            org.deeplearning4j.nn.conf.layers.FeedForwardLayer clonedLayerConf =
                    (org.deeplearning4j.nn.conf.layers.FeedForwardLayer) clone.getLayer();
            int nIn = clonedLayerConf.getNOut(), nOut = clonedLayerConf.getNIn();
            clonedLayerConf.setNIn(nIn);
            clonedLayerConf.setNOut(nOut);

            layer = (Layer) c.newInstance(conf, W.transpose(), b.transpose(), input != null ? input.transpose() : null);
        } catch (Exception e) {
            throw new RuntimeException("unable to construct transposed layer", e);
        }

        return layer;
    }

    @Override
    public void accumulateScore(double accum) {
        score += accum;
    }

    @Override
    public void setInputMiniBatchSize(int size){
    	this.inputMiniBatchSize = size;
    }

    @Override
    public int getInputMiniBatchSize(){
    	return inputMiniBatchSize;
    }
}

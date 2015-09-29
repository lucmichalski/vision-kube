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

package org.deeplearning4j.nn.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.Data;
import lombok.NoArgsConstructor;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.stepfunctions.StepFunction;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A Serializable configuration
 * for neural nets that covers per layer parameters
 *
 * @author Adam Gibson
 */
@Data
@NoArgsConstructor
public class NeuralNetConfiguration implements Serializable,Cloneable {

    private double lr = 1e-1;
    protected int numIterations = 5;
    /* momentum for learning */
    protected double momentum = 0.5;
    /* L2 Regularization constant */
    protected double l2 = 0;
    protected boolean useRegularization = false;
    //momentum after n iterations
    protected Map<Integer,Double> momentumAfter = new HashMap<>();
    //number of line search iterations
    protected int maxNumLineSearchIterations = 5;
    protected OptimizationAlgorithm optimizationAlgo = OptimizationAlgorithm.CONJUGATE_GRADIENT;
    //whether to constrain the gradient to unit norm or not
    protected boolean constrainGradientToUnitNorm = false;
    //adadelta - weight for how much to consider previous history
    protected double rho;
    protected long seed;
    protected StepFunction stepFunction;
    protected Layer layer;
    //gradient keys used for ensuring order when getting and setting the gradient
    protected List<String> variables = new ArrayList<>();
    protected boolean useDropConnect = false;
    // Graves LSTM & RNN
    private int timeSeriesLength = 1;
    //batch size: primarily used for conv nets. Will be reinforced if set.
    protected int batchSize = 10;
    //minimize or maximize objective
    protected boolean minimize = false;
    //l1 regularization
    protected double l1 = 0.0;
    protected double rmsDecay = 0.95;
    protected boolean miniBatch = true;

    /**
     * Creates and returns a deep copy of the configuration.
     */
    @Override
    public NeuralNetConfiguration clone()  {
        try {
            NeuralNetConfiguration clone = (NeuralNetConfiguration) super.clone();
            if(clone.momentumAfter != null) clone.momentumAfter = new HashMap<>(clone.momentumAfter);
            if(clone.layer != null) clone.layer = clone.layer.clone();
            if(clone.stepFunction != null) clone.stepFunction = clone.stepFunction.clone();
            if(clone.variables != null ) clone.variables = new ArrayList<>(clone.variables);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> variables() {
        return new ArrayList<>(variables);
    }

    public void addVariable(String variable) {
        if(!variables.contains(variable))
            variables.add(variable);
    }
    
    public void clearVariables(){
    	variables.clear();
    }

    /**
     * Fluent interface for building a list of configurations
     */
    public static class ListBuilder extends MultiLayerConfiguration.Builder {
        private Map<Integer, Builder> layerwise;

        // Constructor
        public ListBuilder(Map<Integer, Builder> layerMap) {
            this.layerwise = layerMap;
        }


        public ListBuilder backprop(boolean backprop) {
            this.backprop = backprop;
            return this;
        }

        public ListBuilder pretrain(boolean pretrain) {
            this.pretrain = pretrain;
            return this;
        }

        public ListBuilder layer(int ind, Layer layer) {
            if (layerwise.get(0) == null && ind != 0) {
                throw new IllegalArgumentException("LayerZeroIndexError: Layer index must start from 0");
            }
            if (layerwise.size() < ind + 1) {
                throw new IllegalArgumentException("IndexOutOfBoundsError: Layer index exceeds listed size");
            }

            Builder builderWithLayer = layerwise.get(ind).layer(layer);
            layerwise.put(ind, builderWithLayer);
            return this;
        }

        public Map<Integer, Builder> getLayerwise() {
            return layerwise;
        }

        /**
         * Build the multi layer network
         * based on this neural network and
         * overr ridden parameters
         * @return the configuration to build
         */
        public MultiLayerConfiguration build() {
            List<NeuralNetConfiguration> list = new ArrayList<>();
            for(int i = 0; i < layerwise.size(); i++) {
                list.add(layerwise.get(i).build());
            }
            return new MultiLayerConfiguration.Builder().backprop(backprop).inputPreProcessors(inputPreProcessors).
                    pretrain(pretrain).backpropType(backpropType).tBPTTForwardLength(tbpttFwdLength)
                    .tBPTTBackwardLength(tbpttBackLength)
                    .redistributeParams(redistributeParams)
                    .confs(list).build();
        }

    }
    /**
     * Return this configuration as json
     * @return this configuration represented as json
     */
    public String toYaml() {
        ObjectMapper mapper = mapperYaml();

        try {
            String ret =  mapper.writeValueAsString(this);
            return ret;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a neural net configuration from json
     * @param json the neural net configuration from json
     * @return
     */
    public static NeuralNetConfiguration fromYaml(String json) {
        ObjectMapper mapper = mapperYaml();
        try {
            NeuralNetConfiguration ret =  mapper.readValue(json, NeuralNetConfiguration.class);
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return this configuration as json
     * @return this configuration represented as json
     */
    public String toJson() {
        ObjectMapper mapper = mapper();

        try {
            String ret =  mapper.writeValueAsString(this);
            return ret;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a neural net configuration from json
     * @param json the neural net configuration from json
     * @return
     */
    public static NeuralNetConfiguration fromJson(String json) {
        ObjectMapper mapper = mapper();
        try {
            NeuralNetConfiguration ret =  mapper.readValue(json, NeuralNetConfiguration.class);
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Object mapper for serialization of configurations
     * @return
     */
    public static ObjectMapper mapperYaml() {
        return mapperYaml;
    }

    private static final ObjectMapper mapperYaml = initMapperYaml();

    private static ObjectMapper initMapperYaml() {
        ObjectMapper ret = new ObjectMapper(new YAMLFactory());
        ret.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ret.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        ret.enable(SerializationFeature.INDENT_OUTPUT);
        return ret;
    }

    /**
     * Object mapper for serialization of configurations
     * @return
     */
    public static ObjectMapper mapper() {
        return mapper;
    }

    private static final ObjectMapper mapper = initMapper();

    private static ObjectMapper initMapper() {
        ObjectMapper ret = new ObjectMapper();
        ret.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ret.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        ret.enable(SerializationFeature.INDENT_OUTPUT);
        return ret;
    }


    @Data
    public static class Builder implements Cloneable {
        private double rmsDecay = 0.95;
        private double lr = 1e-1f;
        private double momentum = 0.5f;
        private double l2 = 0f;
        private boolean useRegularization = false;
        private Map<Integer, Double> momentumAfter;
        private OptimizationAlgorithm optimizationAlgo = OptimizationAlgorithm.CONJUGATE_GRADIENT;
        private boolean constrainGradientToUnitNorm = false;
        private long seed = System.currentTimeMillis();
        private int numIterations = 5;
        private int timeSeriesLength = 1;
        private StepFunction stepFunction = null;
        private Layer layer;
        private int batchSize = 100;
        private int maxNumLineSearchIterations = 5;
        private boolean minimize = false;
        private double l1 = 0.0;
        private boolean useDropConnect = false;
        private double rho;
        private boolean miniBatch = true;

        /**
         +         * Time series length
         +         * @param timeSeriesLength
         +         * @return
         +         */

        public Builder timeSeriesLength(int timeSeriesLength) {
            this.timeSeriesLength = timeSeriesLength;
            return this;
        }
        
        /**
         * Ada delta coefficient
         * @param rho
         * @return
         */
        public Builder rho(double rho) {
            this.rho = rho;
            return this;
        }


        public Builder miniBatch(boolean miniBatch) {
            this.miniBatch = miniBatch;
            return this;
        }


        /**
         * Use drop connect: multiply the coefficients
         * by a binomial sampling wrt the dropout probability
         * @param useDropConnect whether to use drop connect or not
         * @return the
         */
        public Builder useDropConnect(boolean useDropConnect) {
            this.useDropConnect = useDropConnect;
            return this;
        }

        public Builder l1(double l1) {
            this.l1 = l1;
            return this;
        }


        public Builder rmsDecay(double rmsDecay) {
            this.rmsDecay = rmsDecay;
            return this;
        }

        public Builder minimize(boolean minimize) {
            this.minimize = minimize;
            return this;
        }

        public Builder maxNumLineSearchIterations(int maxNumLineSearchIterations) {
            this.maxNumLineSearchIterations = maxNumLineSearchIterations;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder layer(Layer layer) {
            this.layer = layer;
            return this;
        }

        public Builder stepFunction(StepFunction stepFunction) {
            this.stepFunction = stepFunction;
            return this;
        }

        public ListBuilder list(int size) {
            Map<Integer, Builder> layerMap = new HashMap<>();
            for(int i = 0; i < size; i++)
                layerMap.put(i, clone());
            return new ListBuilder(layerMap);
        }

        @Override
        public Builder clone() {
            try {
                Builder clone = (Builder) super.clone();
                if(clone.momentumAfter != null) clone.momentumAfter = new HashMap<>(clone.momentumAfter);
                if(clone.layer != null) clone.layer = clone.layer.clone();
                if(clone.stepFunction != null) clone.stepFunction = clone.stepFunction.clone();

                return clone;

            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        public Builder iterations(int numIterations) {
            this.numIterations = numIterations;
            return this;
        }

        public Builder learningRate(double lr) {
            this.lr = lr;
            return this;
        }

        public Builder momentum(double momentum) {
            this.momentum = momentum;
            return this;
        }

        public Builder momentumAfter(Map<Integer, Double> momentumAfter) {
            this.momentumAfter = momentumAfter;
            return this;
        }

        public Builder seed(int seed) {
            this.seed = (long) seed;
            Nd4j.getRandom().setSeed(seed);
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            Nd4j.getRandom().setSeed(seed);
            return this;
        }


        public Builder l2(double l2) {
            this.l2 = l2;
            return this;
        }

        public Builder regularization(boolean useRegularization) {
            this.useRegularization = useRegularization;
            return this;
        }

        public Builder optimizationAlgo(OptimizationAlgorithm optimizationAlgo) {
            this.optimizationAlgo = optimizationAlgo;
            return this;
        }

        public Builder constrainGradientToUnitNorm(boolean constrainGradientToUnitNorm) {
            this.constrainGradientToUnitNorm = constrainGradientToUnitNorm;
            return this;
        }

        /**
         * Return a configuration based on this builder
         *
         * @return
         */
        public NeuralNetConfiguration build() {
            if (layer == null)
                throw new IllegalStateException("No layer defined.");

            NeuralNetConfiguration conf = new NeuralNetConfiguration();

            conf.minimize = minimize;
            conf.maxNumLineSearchIterations = maxNumLineSearchIterations;
            conf.l1 = (!Double.isNaN(layer.getL1()) ? layer.getL1() : l1);
            conf.batchSize = batchSize;
            conf.layer = layer;
            conf.lr = (!Double.isNaN(layer.getLr()) ? layer.getLr() : lr);
            conf.numIterations = numIterations;
            conf.momentum = momentum;
            conf.l2 = (!Double.isNaN(layer.getL2()) ? layer.getL2() : l2);
            conf.useRegularization = useRegularization;
            conf.momentumAfter = momentumAfter;
            conf.optimizationAlgo = optimizationAlgo;
            conf.constrainGradientToUnitNorm = constrainGradientToUnitNorm;
            conf.seed = seed;
            conf.timeSeriesLength = timeSeriesLength;
            conf.rmsDecay = rmsDecay;
            conf.stepFunction = stepFunction;
            conf.useDropConnect = useDropConnect;
            conf.miniBatch = miniBatch;
            conf.rho = rho;

            return conf;
        }

    }
}

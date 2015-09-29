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

package org.deeplearning4j.nn.conf.preprocessor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.gradient.Gradient;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Composable input pre processor
 * @author Adam Gibson
 */
@Data
public class ComposableInputPreProcessor extends BaseInputPreProcessor {
	private InputPreProcessor[] inputPreProcessors;

    @JsonCreator
    public ComposableInputPreProcessor(@JsonProperty("inputPreProcessors") InputPreProcessor[] inputPreProcessors) {
        this.inputPreProcessors = inputPreProcessors;
    }

    @Override
    public INDArray preProcess(INDArray input, Layer layer) {
        for(InputPreProcessor preProcessor : inputPreProcessors)
        input = preProcessor.preProcess(input,layer);
        return input;
    }

    @Override
    public INDArray backprop(INDArray output, Layer layer) {
        for(InputPreProcessor inputPreProcessor : inputPreProcessors)
            output = inputPreProcessor.backprop(output,layer);
        return output;
    }

    @Override
    public ComposableInputPreProcessor clone() {
        ComposableInputPreProcessor clone = (ComposableInputPreProcessor) super.clone();
        if(clone.inputPreProcessors != null) {
            InputPreProcessor[] processors = new InputPreProcessor[clone.inputPreProcessors.length];
            for(int i = 0; i < clone.inputPreProcessors.length; i++) {
                processors[i] = clone.inputPreProcessors[i].clone();
            }
            clone.inputPreProcessors = processors;
        }
        return clone;
    }
}

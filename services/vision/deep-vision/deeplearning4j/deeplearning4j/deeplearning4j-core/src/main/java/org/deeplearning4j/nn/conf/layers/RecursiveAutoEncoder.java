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

package org.deeplearning4j.nn.conf.layers;

import lombok.*;

import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.distribution.Distribution;
import org.deeplearning4j.nn.weights.WeightInit;

/**
*
* Recursive AutoEncoder.
* Uses back propagation through structure.
*
*/
@Data @NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RecursiveAutoEncoder extends FeedForwardLayer {

    private RecursiveAutoEncoder(Builder builder) {
    	super(builder);
    }

    public static class Builder extends FeedForwardLayer.Builder<Builder> {

        @Override
        @SuppressWarnings("unchecked")
        public RecursiveAutoEncoder build() {
            return new RecursiveAutoEncoder(this);
        }
    }
}

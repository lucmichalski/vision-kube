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

package org.deeplearning4j.nn.weights;


import org.apache.commons.math3.util.FastMath;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.api.rng.distribution.Distribution;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;


/**
 * Weight initialization utility
 *
 * @author Adam Gibson
 */
public class WeightInitUtil {


  /**
   * Normalized weight init
   *
   * @param shape shape
   * @param nIn   number of inputs
   * @return the weights
   */
  public static INDArray normalized(int[] shape, int nIn) {
    return Nd4j.rand(shape).subi(0.5).divi((double) nIn);
  }

  /**
   * Generate a random matrix with respect to the number of inputs and outputs.
   * This is a bound uniform distribution with the specified minimum and maximum
   *
   * @param shape the shape of the matrix
   * @param nIn   the number of inputs
   * @param nOut  the number of outputs
   * @return {@link INDArray}
   */
  public static INDArray uniformBasedOnInAndOut(int[] shape, int nIn, int nOut) {
    double min = -4.0 * Math.sqrt(6.0 / (double) (nOut + nIn));
    double max = 4.0 * Math.sqrt(6.0 / (double) (nOut + nIn));
    return Nd4j.rand(shape,Nd4j.getDistributions().createUniform(min,max));
  }

  public static INDArray initWeights(int[] shape, float min, float max) {
    return Nd4j.rand(shape, min, max, Nd4j.getRandom());
  }


  /**
   * Initializes a matrix with the given weight initialization scheme
   *
   * @param shape      the shape of the matrix
   * @param initScheme the scheme to use
   * @return a matrix of the specified dimensions with the specified
   * distribution based on the initialization scheme
   */
  public static INDArray initWeights(int[] shape, WeightInit initScheme,
                                     Distribution dist) {
    INDArray ret;
    switch (initScheme) {
      case NORMALIZED:
        ret = Nd4j.rand(shape, Nd4j.getRandom());
        return ret.subi(0.5).divi(shape[0]);
      case XAVIER:
        ret = Nd4j.randn(shape).divi(FastMath.sqrt(shape[0] + shape[1]));
        return ret;
      case UNIFORM:
        double a = 1 / (double) shape[0];
        return Nd4j.rand(shape, -a, a, Nd4j.getRandom());
      case VI:
        ret = Nd4j.rand(shape, Nd4j.getRandom());
        int len = 0;
        for (int aShape : shape) {
          len += aShape;
        }
        double r = Math.sqrt(6) / Math.sqrt(len + 1);
        ret.muli(2).muli(r).subi(r);
        return ret;
      case DISTRIBUTION:
        ret = dist.sample(shape);
        return ret;
      case SIZE:
        return uniformBasedOnInAndOut(shape, shape[0], shape[1]);
      case ZERO:
        return Nd4j.create(shape);


    }

    throw new IllegalStateException("Illegal weight init value");
  }

  /**
   * Initializes a matrix with the given weight initialization scheme
   *
   * @param nIn        the number of rows in the matrix
   * @param nOut       the number of columns in the matrix
   * @param initScheme the scheme to use
   * @return a matrix of the specified dimensions with the specified
   * distribution based on the initialization scheme
   */
  public static INDArray initWeights(int nIn, int nOut, WeightInit initScheme, Distribution dist) {
    return initWeights(new int[]{nIn, nOut}, initScheme, dist);
  }


}

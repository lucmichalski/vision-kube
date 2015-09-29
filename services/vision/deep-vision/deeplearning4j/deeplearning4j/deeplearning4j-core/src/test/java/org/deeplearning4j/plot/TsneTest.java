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

package org.deeplearning4j.plot;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.util.List;

/**
 * Created by agibsonccc on 10/1/14.
 */
public class TsneTest {

    @Test
    public void testTsne() throws Exception {
        Nd4j.dtype = DataBuffer.Type.DOUBLE;
        Tsne calculation = new Tsne.Builder().setMaxIter(1).usePca(false).setSwitchMomentumIteration(20)
                .normalize(true).useAdaGrad(false).learningRate(500).perplexity(20).minGain(1e-1f)
                .build();
        ClassPathResource resource = new ClassPathResource("/mnist2500_X.txt");
        File f = resource.getFile();
        INDArray data = Nd4j.readNumpy(f.getAbsolutePath(),"   ");
        ClassPathResource labels = new ClassPathResource("mnist2500_labels.txt");
        List<String> labelsList = IOUtils.readLines(labels.getInputStream());

        calculation.plot(data,2, labelsList);


    }


}

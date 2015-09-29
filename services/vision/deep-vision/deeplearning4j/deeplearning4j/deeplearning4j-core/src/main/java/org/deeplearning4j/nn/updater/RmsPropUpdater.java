package org.deeplearning4j.nn.updater;

import org.deeplearning4j.nn.api.Layer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.GradientUpdater;

/**
 * @author Adam Gibson
 */
public class RmsPropUpdater extends BaseUpdater {


    @Override
    public void init() {

    }

    @Override
    public GradientUpdater init(String variable, INDArray gradient, Layer layer) {
        org.nd4j.linalg.learning.RmsPropUpdater rmsprop = (org.nd4j.linalg.learning.RmsPropUpdater) updaterForVariable.get(variable);
        if(rmsprop == null) {
            rmsprop = new org.nd4j.linalg.learning.RmsPropUpdater();
            updaterForVariable.put(variable,rmsprop);
            rmsprop.setRmsDecay(layer.conf().getRmsDecay());
            rmsprop.setLR(layer.conf().getLr());
        }

        return rmsprop;
    }
}

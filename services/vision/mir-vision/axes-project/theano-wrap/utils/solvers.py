import numpy as np
import theano

from collections import OrderedDict

import logging
log = logging.getLogger('solvers')


class SGDSolver(object):

    def __init__(self):

        self.momentum = 0.9
        self.learning_rate = 0.01

    def compute_updates(self, params, grads, learning_rate_mul=None):

        updates = OrderedDict()

        for param, grad in zip(params, grads):
            param_name = param.name[:-2] # param.name is of form <name>.W
            lr = self.learning_rate
            if learning_rate_mul is not None and param_name in learning_rate_mul:
                lr *= learning_rate_mul[param_name]

            value = param.get_value(borrow=True)
            velocity = theano.shared(np.zeros(value.shape, dtype=value.dtype),
                                     broadcastable=param.broadcastable)
            x = self.momentum*velocity - lr*grad

            updates[velocity] = x
            updates[param] = x + param

        return updates

#!/usr/bin/env python
"""
References:
    http://deeplearning.net/software/theano/tutorial/using_gpu.html#testing-theano-with-gpu

python `python -c "import os, theano; print os.path.dirname(theano.__file__)"`/misc/check_blas.py
"""
from __future__ import absolute_import, division, print_function
import theano
#from theano import function, config, shared, sandbox
import theano.tensor as T
import numpy
import time


def test_theano():
    vlen = 10 * 30 * 768  # 10 x #cores x # threads per core
    iters = 1000

    rng = numpy.random.RandomState(22)
    x = theano.shared(numpy.asarray(rng.rand(vlen), theano.config.floatX))
    f = theano.function([], T.exp(x))
    print(f.maker.fgraph.toposort())
    t0 = time.time()
    for i in range(iters):
        r = f()
    t1 = time.time()
    print('Looping %d times took' % iters, t1 - t0, 'seconds')
    print('Result is', r)

    if numpy.any([isinstance(x_.op, T.Elemwise) for x_ in f.maker.fgraph.toposort()]):
        print('Used the cpu')
    else:
        print('Used the gpu')

if __name__ == '__main__':
    """
    python $CODE_DIR/ibeis_cnn/test_theano.py
    """
    test_theano()
    #theano.test()

"""
References:
    http://deeplearning.net/software/theano/library/config.html

Check Settings:
    python -c 'import theano; print theano.config' | less
"""
import utool as ut
import os
from os.path import join

"""
CommandLine:
    cd %CODE_DIR%/ibies_cnn/code
    cd $CODE_DIR/ibies_cnn/code
    code
    cd ibeis_cnn/code
    python train.py

PythonPrereqs:
    pip install theano
    pip install git+https://github.com/Lasagne/Lasagne.git
    pip install git+git://github.com/lisa-lab/pylearn2.git
    #pip install lasagne
    #pip install pylearn2
    git clone git://github.com/lisa-lab/pylearn2.git
    git clone https://github.com/Lasagne/Lasagne.git
    cd pylearn2
    python setup.py develop
    cd ..
    cd Lesagne
    git checkout 8758ac1434175159e5c1f30123041799c2b6098a
    python setup.py develop
"""


def init_theanorc():
    theanorc_fpath = join(os.getenv('HOME'), '.theanorc')
    theanorc_text = ut.codeblock(
        '''
        [global]
        floatX = float32
        device = gpu0
        openmp = True

        [nvcc]
        fastmath = True
        '''
    )
    if ut.checkpath(theanorc_fpath, verbose=True):
        if not ut.arg_you_sure('overwrite?'):
            return
    ut.write_to(theanorc_fpath, theanorc_text)


if __name__ == '__main__':
    init_theanorc()

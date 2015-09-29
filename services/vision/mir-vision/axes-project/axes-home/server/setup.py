#!/usr/bin/env python
#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
from distutils.core import setup

setup(
    name = 'axes-home',
    version = '1.0',
    description = 'AXES home user interface',
    author = 'Kevin McGuinness',
    author_email = 'kevin.mcguinness@dcu.ie',
    url = 'https://bitbucket.org/kevinmcguinness/axes-home',
    cmdclass = { 'build_ext': build_ext },
    packages = ['axeshome'],
    classifiers = [
        'Intended Audience :: Science/Research',
        'Intended Audience :: Developers',
        'Programming Language :: Python',
        'Topic :: Software Development',
        'Topic :: Scientific/Engineering',
        'Operating System :: MacOS :: MacOS X',
        'Operating System :: POSIX',
        'Operating System :: Unix',
        'Operating System :: Microsoft :: Windows'
    ]
)
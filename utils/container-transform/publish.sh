#!/bin/bash
# Pypi publish script
pip install wheel
pip install -e .[all]
python setup.py clean --all
python setup.py sdist
pip wheel --no-index --no-deps --wheel-dir dist dist/*.tar.gz
python setup.py register sdist bdist_wheel upload

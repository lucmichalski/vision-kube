""" Utilities for computing unique fingerprints for data. """

import hashlib

def _hashfile(afile, hasher, blocksize=65536):
    buf = afile.read(blocksize)
    while len(buf) > 0:
        hasher.update(buf)
        buf = afile.read(blocksize)
    return hasher.digest()

def md5sum(filename):
    result = _hashfile(open(filename, 'rb'), hashlib.md5())
    return result

def md5sum_string(input_string):
    hash_object = hashlib.md5(input_string)
    return hash_object.hexdigest()

from __future__ import print_function

from multiprocessing.dummy import Pool as ThreadPool
from multiprocessing import Pool as ProcessPool

from math import factorial


def factorial_calc(n):
    try:
        return (n, factorial(n))
    except Exception, e:
        return (n, e)

def calc_factorials(max_int=100, pool_size=8, threads=True, chunk_size=10):
    
    if threads:
        pool = ThreadPool(pool_size)
    else:
        pool = ProcessPool(pool_size)
        
    results = pool.imap_unordered(factorial_calc, range(max_int), chunk_size)

    
    return results

if __name__ == '__main__':
    for (i, result) in enumerate(calc_factorials(max_int=10000, pool_size=8, threads=False, chunk_size=20)):
        print ("\r>>result: %d" % i, end="")
        

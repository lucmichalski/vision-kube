import time, sys, random, cPickle
import numpy
import os

VERBOSE = os.getenv("VERBOSE")

from yael import ynumpy, threads, yael




########### A few small functions


  

def params_to_tuple(params):
  "convert param block to tuple (suitable as key in a hash table) " 
  if params == None: return None
  return tuple(sorted(params.items()))


class CrossvalOptimization:
  """
  Optimizes the parameters _lambda bias_term eta0 beta using
  cross-validation. First call the constructor, adjust optimization
  parameters and call optimize(), which returns the set of optimal
  parameters it could find.

  """

  def __init__(self, Xtrain, Ltrain):

    self.eval_freq = 1
  
    # starting point for optimization (also specifies which parameters
    # should be considered for optimization)
    self.init_point = {}
    
    # additional parameters to jsgd_train
    self.constant_parameters = {}

    # nb of cross-validation folds to use
    self.nfold = 5
    
    # consider all starting points that are within 5% of best seen so far 
    self.score_tol = 0.05
    
    # this tolerance decreases by this ratio at each iteration
    self.score_tol_factor = 0.5 

    # maximum number of iterations
    self.max_iter = 10000

    self.Xtrain = Xtrain
    self.Ltrain = Ltrain

    # ranges for parameters in init_point
    self.ranges = {}

    # if not False, self.accuracy_function is called 
    self.default_accuracy_function = True

    # use multithreading if training itself is not threaded 
    self.n_thread = 1

    # how are scores accross folds combined?
    self.fold_combination = 'avg'
    
    # all params can be changed after constructor


    
  def get_fold(self, fold):
    """ return the train and validation indices for a fold (in 0:nfold) """
    
    n = self.Xtrain.shape[0]

    # prepare fold
    i0 = fold * n / self.nfold
    i1 = (fold + 1) * n / self.nfold
    
    valid_i = numpy.arange(i0, i1)
    train_i = numpy.hstack([numpy.arange(0, i0), numpy.arange(i1, n)])
    
    return train_i, valid_i

  def accuracy_function(self, fold, scores, y):
    """ this accuracy function is called instead of the default one if
    default_accuracy_function != 1. It should return a value in [0, 1] """

    return 0.0
    

  def eval_params(self, params, fold):

    train_i, valid_i = self.get_fold(fold)


    assert False, "implement learn here!"

    return stats

  def params_step(self, name, pm):
    " step parameter no in direction pm "
    new_params = self.params.copy()
    if name == None and pm == 0: return new_params
    curval = self.params[name]
    r = self.ranges[name]
    i = r.index(curval)
    if 0 <= i + pm < len(r):
      new_params[name] = r[i + pm]
      return new_params
    else:
      return None


  def do_exp(self, (pname, pm, fold)):
    # perform experiment if it is not in cache
    params_i = self.params_step(pname, pm)

    k = (params_to_tuple(params_i), fold)
    if k in self.cache:
      res = self.cache[k]
    else: 
      res = self.eval_params(params_i, fold)
      self.cache[k] = res

    return (pname, pm, fold), res


    
  def optimize(self):

    nfold = self.nfold
    
    # cache for previous results
    self.cache = dict([((None,i),None) for i in range(self.nfold)])
    
    # best scrore so far
    best_score = -1.0

    t0 = time.time()
    it = 0

    queue = [(0, self.init_point)]

    while queue:

      # pop the best configuration from queue
      score_p, self.params = queue.pop()
      print "============ iteration %d (%.1f s): %s" % (
        it, time.time() - t0, self.params)
      if VERBOSE != None:
        print "baseline score %.3f, remaining queue %d (score > %.3f)"  % (
         score_p * 100, len(queue), (best_score - self.score_tol) * 100)

      # extend in all directions
      todo = [(pname, pm, fold)
              for pname in self.params
              for pm in -1, 1
              for fold in range(nfold)]

      if it == 0:
        # add the baseline, which has not been evaluated so far
        todo = [(None, 0, fold) for fold in range(nfold)] + todo

      # filter out configurations that have been visited already
      todo = [(pname, pm, fold) for (pname, pm, fold) in todo if
              (params_to_tuple(self.params_step(pname, pm)), 0) not in self.cache]


      # perform all experiments 
      src = threads.ParallelIter(self.n_thread, todo, self.do_exp)  

      while True: 

        # pull a series of nfold results 
        try:   
          allstats = [ next(src) for j in range(nfold) ]
        except StopIteration:
          break

        (pname, pm, zero), stats = allstats[0]
        assert zero == 0
        params_i = self.params_step(pname, pm)

        # no stats for this point (may be invalid)
        if stats == None: continue

        params_key = params_to_tuple(params_i)

        # make a matrix of validation accuracies    
        valid_accuracies = numpy.vstack([stats.valid_accuracies for k, stats in allstats])

        # take max of average over epochs
        if self.fold_combination == "avg":
          avg_scores = valid_accuracies.sum(0)
          score = avg_scores.max() / nfold
        elif self.fold_combination == "min":
          scores = valid_accuracies.min(0)
        else: assert False
        
        sno = avg_scores.argmax()
        epoch = sno * self.eval_freq
        if VERBOSE != None:
          print "  %s, epoch %d, score = %.3f [%.3f, %.3f]" % (
            params_i, epoch,
            score * 100, 100 * valid_accuracies[:, sno].min(),
            100 * valid_accuracies[:, sno].max())
        
        if score >= best_score:
          # we found a better score!
          #print "  keep"
          if score > best_score:
            best_op = []
            best_score = score
          best_op.append((params_i, epoch))

        # add this new point to queue
        queue.append((score, params_i))

        sys.stdout.flush()

      # strip too low scores from queue 
      queue = [(score, k) for score, k in queue if score > best_score - self.score_tol]

      # sorted by increasing scores (best one is last)
      queue.sort() 

      it += 1
      self.score_tol *= self.score_tol_factor

      if it > self.max_iter: break
      
    if VERBOSE != None:
      print "best params found: score %.3f" % (best_score * 100)
      for params, epoch in best_op: 
        print params, epoch
    
    return [(params, epoch) for params, epoch in best_op]
    


  



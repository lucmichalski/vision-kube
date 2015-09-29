

def test_sift_patchmatch():
    import pyhesaff
    X_sift = pyhesaff.extract_desc_from_patches(X_test)
    import numpy as np
    sqrddist = ((X_sift[::2].astype(np.float32) - X_sift[1::2].astype(np.float32)) ** 2).sum(axis=1)
    test.test_siamese_thresholds(sqrddist[None, :].T, y_test)


def test_siamese_thresholds(prob_list, y_test):
    """
    Test function to see how good of a threshold we can learn

    network_output = prob_list
    """
    import vtool as vt
    # batch cycling may cause more outputs than test labels.
    # should be able to just crop
    network_output = prob_list[0:len(y_test)].copy()
    tp_support = network_output.T[0][y_test.astype(np.bool)].astype(np.float64)
    tn_support = network_output.T[0][~(y_test.astype(np.bool))].astype(np.float64)
    if tp_support.mean() < tn_support.mean():
        print('need to invert scores')
        tp_support *= -1
        tn_support *= -1
    bottom = min(tn_support.min(), tp_support.min())
    if bottom < 0:
        print('need to subtract from scores')
        tn_support -= bottom
        tp_support -= bottom

    vt.score_normalization.rrr()
    vt.score_normalization.test_score_normalization(tp_support, tn_support, with_scores=False)

    #from ibeis.model.hots import score_normalization
    #test_score_normalization
    #learnkw = dict()
    #learntup = score_normalization.learn_score_normalization(
    #    tp_support, tn_support, return_all=False, **learnkw)
    #(score_domain, p_tp_given_score, clip_score) = learntup
    # Plotting
    #import plottool as pt
    #fnum = 1
    #pt.figure(fnum=fnum, pnum=(2, 1, 1), doclf=True, docla=True)
    #score_normalization.plot_support(tn_support, tp_support, fnum=fnum, pnum=(2, 1, 1))
    #score_normalization.plot_postbayes_pdf(
    #    score_domain, 1 - p_tp_given_score, p_tp_given_score, fnum=fnum, pnum=(2, 1, 2))
    #pass

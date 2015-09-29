#! /usr/bin/python
# -*- coding: utf-8 -*-

"""
compute_scores.py

Computes scores for multiple events and multiple videos in one pass.
Stores results in flat text files, one for each event class, see --output-directory.

Xavier Martin <xavier.martin@inria.fr>
"""


##############################################################################
# Modules importation #

# standard modules
import numpy as np
import os
import os.path
import sys
import pdb

try:
    import cPickle as pickle
except:
    import pickle

# custom module
import vd_utils

from yael import ynumpy
from multiprocessing import Pool
import subprocess
import uuid

MED_DIR = os.getenv("MED_BASEDIR")
COLLECTION_DIR = os.getenv("COLLECTION_DIR")


def usage():
    print "\ncompute_scores.py"

class Event:
    def __init__(self, name):
        self.name = name
        self.classifier = None
        self.normalizer = None
        self.descriptors = []
        #self.scores = []
        
output_directory = ""

events = {} # eventName -> Event
unionRequiredDescs = []   
eventList = []

processUUID = uuid.uuid1()

def computeScoresSingleVideo(vid):
    # Load video descriptors
    print "Computing scores for %s" % vid
    
    # Find location of descriptors
    descriptorsLocation = ""
    oneFilePerShot = False
    
    # TODO: properly test one-file-per-shot
    if os.path.exists(COLLECTION_DIR + "/processing/videos_workdir/" + vid + "/shots/%09d/" % (1) + unionRequiredDescs[0] + ".fvecs"):
        oneFilePerShot = True
        descriptorsLocation = COLLECTION_DIR + "/processing/videos_workdir/" + vid + "/shots/"
    elif os.path.exists(COLLECTION_DIR + "/processing/videos_workdir/" + vid + "/shots/" + unionRequiredDescs[0] + ".fvecs"):
        descriptorsLocation = COLLECTION_DIR + "/processing/videos_workdir/" + vid + "/shots/"
    elif os.path.exists(COLLECTION_DIR + "/processing/videos_workdir/" + vid + "/" + unionRequiredDescs[0] + ".fvecs"):
        descriptorsLocation = COLLECTION_DIR + "/processing/videos_workdir/" + vid + "/"
    else:
        print "Error: couldn't find descriptors for " + vid + "."
        return 1
        
    # Load all required channels
    vidDescs = {}
    if oneFilePerShot:
        
        for desc in unionRequiredDescs:
            vidDescs[desc] = []
        shotNb = 1
        while os.path.exists(descriptorsLocation + "%09d/" % shotNb + unionRequiredDescs[0] + ".fvecs"):
            for desc in unionRequiredDescs:
                vidDescs[desc].append(ynumpy.fvecs_read(descriptorsLocation + "%09d/" % shotNb + desc + ".fvecs"))
            shotNb += 1
                
    else:
        for desc in unionRequiredDescs:
            vidDescs[desc] = ynumpy.fvecs_read(descriptorsLocation + desc + ".fvecs")
    
    # Compute
    # For every event:
    eventScores = {}
    for eventName in eventList:
        
        event = events[eventName]
        eventScores[eventName] = []
        
        # Normalize fisher vectors
        normalizedVidDescs = {}            
        for desc in event.descriptors:
            mu = event.normalizer[desc][0]
            sigma = event.normalizer[desc][1]
            
            normalizedVidDescs[desc] = vd_utils.standardize(vidDescs[desc], mu, sigma)
            normalizedVidDescs[desc] = vd_utils.normalize_fisher(normalizedVidDescs[desc])
            normalizedVidDescs[desc] = normalizedVidDescs[desc].astype(np.float32)
        
        # Retrieve classifier data
        weights, bias = event.classifier[:2]
        
        # Compute score for every shot
        for shotNb in range(len(normalizedVidDescs[event.descriptors[0]])):
            
            score = 0.0
            # Accumulate scores for all descriptor channels
            for descNb in range(len(event.descriptors)):
                score += np.dot(weights[descNb], normalizedVidDescs[event.descriptors[descNb]][shotNb])
            
            eventScores[eventName].append([score, shotNb+1, vid])
            
    # Lock score files
    subprocess.call('lockfile -0 /tmp/compute_scores_%s.lock' % processUUID, shell=True)
    #while (subprocess.call('set -o noclobber; echo locked > /tmp/compute_scores_%s.lock' % processUUID, shell=True)
    
    # Output scores
    # FOR EVERY EVENT    
    for eventName in events:
        event = events[eventName]
                
        f = open(output_directory + "/" + event.name + ".scores", 'a')
        #for s in sorted(event.scores, reverse=True):
        for s in eventScores[eventName]:
            #print s
            f.write("%f %d %s\n" % (s[0], s[1], s[2]))
        f.close()
    
    # Unlock score files
    subprocess.call('rm -f /tmp/compute_scores_%s.lock' % processUUID, shell=True)
    return 0

def main():
    global output_directory
    
    infisher = ""
    
    

    videoList = []
    

    # parse arguments
    args=sys.argv[1:]
    while args:
        a=args.pop(0)
        if a in ('-h','--help'):
            usage()
            sys.exit(0)
        elif a in ('--videos','-v'):
            f = open(args.pop(0))
            for vid in f.read().splitlines():
                videoList.append(vid)
            f.close()
        elif a in ('--events','-e'):
            f = open(args.pop(0))
            for ev in f.read().splitlines():
                eventList.append(ev)
            f.close()
        elif a in ('--infisher','-i'):   infisher = args.pop(0)
        elif a in ('--output-directory','-o'):    output_directory = args.pop(0)
        else:
            print "unknown option", a
            usage()
    """
    if vidname == "" or collection == "" or infisher == "" or output_directory == "":
        print "ERROR - missing options"
        usage()
        sys.exit(1)
    """
    """
    if not os.path.isdir(infisher) or not os.path.isdir(output_directory):
        print "ERROR - specified path not a directory - Exiting..."
        sys.exit(1)
    """
    
    if len(eventList) < 1:
        print "ERROR: need at least one event."
        sys.exit(1)
    elif len(videoList) < 1:
        print "ERROR: need at least one video."
        sys.exit(1)
    elif output_directory == "":
        print "ERROR: need to specify an output directory for the score files"
        sys.exit(1)
    
    # Setup
    # -------
    
    

    # for all events, load classifier + normalizer, compile list of required descriptors. makedirs.
    for eventName in eventList:
        print "Loading classifier and normalizer for", eventName
        eventObj = Event(eventName)
        eventNameShort = eventName.split('/')[-1:][0]
        eventObj.classifier = pickle.load(open(COLLECTION_DIR + "/events/" + eventName + "/classifiers/" + eventNameShort + "_classifier.pickle"))
        eventObj.normalizer = pickle.load(open(COLLECTION_DIR + "/events/" + eventName + "/classifiers/" + eventNameShort + "_classifier_normalizer.pickle"))
        
        # append the required descs for this event
        channelList = open(COLLECTION_DIR + "/events/" + eventName + "/workdir/channels.list")
        for c in channelList.read().splitlines():
            
            listDesc = open(MED_DIR + "compute_descriptors/" + c + "/" + c + "_descriptors.list")
            for desc in listDesc.read().splitlines():
                
                eventObj.descriptors.append(desc)
                if desc not in unionRequiredDescs:
                    print "Appending", desc, "to the list of required descriptors."
                    unionRequiredDescs.append(desc)
                    
        events[eventName] = eventObj
        
        # Creating score output path
        scorePath = output_directory + "/" + eventObj.name
        scorePath = scorePath.split('/')[:-1]
        scorePath = '/'.join(scorePath)
        try: 
            os.makedirs(scorePath)
        except OSError:
            if not os.path.isdir(scorePath):
                raise
    # {unionRequiredDescs == union of required descs for all events}
    
    # Process videos
    # --------------
    
    
    p = Pool(32)
    p.map_async(computeScoresSingleVideo, videoList).get(9999999)
    # map is not used because of a python bug
    # http://stackoverflow.com/questions/1408356/keyboard-interrupts-with-pythons-multiprocessing-pool
    
    p.close()
    


    # {processed all videos}            

    # sort scores
    for eventName in events:
        print "Sorting scores for", eventName
        scoresFilename = output_directory + "/" + eventName + ".scores"
        subprocess.call('sort -rn "%s" > "%s.tmp"' % (scoresFilename, scoresFilename), shell=True)
        subprocess.call('mv "%s.tmp" "%s"' % (scoresFilename, scoresFilename), shell=True)
    
    # save results
    """
    for eventName in events:
        event = events[eventName]
        print "Saving scores for", event.name
        
        scorePath = output_directory + "/" + event.name
        scorePath = scorePath.split('/')[:-1]
        scorePath = '/'.join(scorePath)
        try: 
            os.makedirs(scorePath)
        except OSError:
            if not os.path.isdir(scorePath):
                raise
                
        f = open(output_directory + "/" + event.name + ".scores", 'w')
        for s in sorted(event.scores, reverse=True):
            print s
            f.write("%f %d %s\n" % (s[0], s[1], s[2]))
        f.close()
    """
    return 0


if __name__ == '__main__':
    main()

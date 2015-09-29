#! /usr/bin/python
# -* encoding: utf-8 *-

"""
SYNOPSIS
scores_http_server

Listens for HTTP queries such as "Give me the 1000 best scores for event E".
Parses the corresponding event scores file and outputs the 1000 first results in JSON.
To update scores, overwrite the scores text file. No cache is kept.
        
SAMPLE QUERIES    
    curl http://localhost:12080/api_get_list_of_classifiers
    curl http://localhost:12080/api_get_classifier_output?classifiername=EVENTNAME\&nb_results=10
    curl http://localhost:12080/api_get_list_of_datasets


Xavier Martin <xavier.martin@inria.fr>
(based on Clément Leray's work)
"""


##############################################################################
# Modules importation #

# standard modules
import sys
import traceback
import json
import collections
import itertools
import os
import os.path
import BaseHTTPServer
import subprocess


##############################################################################
# class declaration #

#----------------------------------------------------------#
# customized handler based on BaseHTTPRequestHandler class #
#----------------------------------------------------------#
class EventRecognitionHandler(BaseHTTPServer.BaseHTTPRequestHandler): 
    """ 
        class handling and parsing event recognition user requests,
        and launching proper service with results gathering and sending

        - Methods :
            - do_GET      -> handle http GET request
            - command_GET -> execute action corresponding to the GET request
    """
    # constructor
    def __init__(self, datasetname, scoredir, *args, **kwargs):
        self.datasetname = datasetname
        self.scoredir = scoredir
        
        BaseHTTPServer.BaseHTTPRequestHandler.__init__(self, *args, **kwargs)


### METHODS ###

# main methods

    # method handling http GET request
    def do_GET(self): 
        """ 
            Parse the url to determine which command to execute 
        """
        # search for ? in the url to split command and arguments
        qmark = self.path.find('?')
        if qmark == -1:
            command = self.path
            args = None
        else:
            command = self.path[:qmark]
            args = self.path[qmark+1:]
        try:
            self.command_GET(command, args)
        except Exception, e:
            traceback.print_exc(50,sys.stderr)
            self.header_error_404()
            print >>self.wfile,"Exception",e                         
    

    # method executing submitted command on database(s)
    def command_GET(self, command, args):
        """ 
            Execute method corresponding to the command
        """
        if command == "/api_get_list_of_datasets":
            self.get_list_of_datasets_json()
            #elif command == "/api_get_list_of_videos":
            #    self.get_list_of_videos_json(args)
        elif command == "/api_get_list_of_classifiers":
            self.get_list_of_classifiers_json(args)
        elif command == "/api_get_classifier_output":
            self.get_classifier_output_ranked(args)
            #elif command == "/api_get_classifier_feedback":
            #    self.get_classifier_feedback(args)
        else:
            print>>self.wfile,"Error : unknown command\n"
            return -1


# event recognition methods #

    # send to client in json format the list of indexed datasets
    def get_list_of_datasets_json(self):
        datasetDict = {}
        datasetDict["datasets"] = []
        datasetDict["datasets"].append(self.datasetname)
        self.header_json()
        print>>self.wfile, json.dumps(datasetDict)
    """
    # send to client in josn format the list of videos of the considered classifier
    def get_list_of_videos_json(self, args):
        argDict = self.get_args(args, 1)
        if argDict == -1:
            return -1
        ds = argDict.get("datasetname", "none")
        for dataset in self.base.datasets:
            if ds == dataset.name:
                videoDict = {}
                videoDict["videos"] = []
                for vid in dataset.vidList:
                    videoDict["videos"].append(vid.name)
                self.header_json()
                print>>self.wfile,json.dumps(videoDict)
    """         

    # send to client in json format the list of considered classifiers
    def get_list_of_classifiers_json(self, args):
        argDict = self.get_args(args, 1)
        if argDict == -1:
            return -1
        #ds = argDict.get("datasetname", "none")
        #if ds == self.datasetname:
        classifierDict = {}
        classifierDict["classifiers"] = []
        #for i in allClassifiers:
        #    classifierDict["classifiers"].append(i)
            
        # Get list of classifiers from the onthefly directory
        scoreFiles = os.listdir(self.scoredir)
        for i in scoreFiles:
            evName = '.'.join(i.split('.')[:-1])
            evName = evName.replace(' ', '_')
            classifierDict["classifiers"].append(evName)
            
        self.header_json()
        print>>self.wfile,json.dumps(classifierDict)
        return 0
        #else:
        #    print>>self.wfile,"Error : unknown dataset\n"
        #    return -1

    """
    # send to client in json format ranked videos with scores in json format
    def get_classifier_output(self, args):
        status = 0
        argDict = self.get_args(args, 4)
        if argDict == -1:
            argDict = self.get_args(args, 3)
            if argDict == -1:
                return -1

        ds = argDict.get("datasetname", "none")
        nb_results = argDict.get("nb_results", 100)
        for dataset in self.base.datasets:
            if ds == dataset.name:
                status = 1
                classifier = argDict.get("classifiername", "none")
                i_Class = vd_utils.get_classifier_index_from_name(classifier)
                if i_Class in dataset.classifierList:
                    scoresDict = self.get_scores(dataset, i_Class, nb_results)
                    self.header_json()
                    print>>self.wfile,json.dumps(scoresDict)
                else:
                    self.header_error_404()
                    print>>self.wfile,"Error : classifier not found in dataset\n"
        if status == 0:
            print>>self.wfile,"Error : unknown dataset\n"
    """
    
    def get_classifier_output_ranked(self, args):
        argDict = self.get_args(args, 3)
        if argDict == -1:
            argDict = self.get_args(args, 2)
            if argDict == -1:
                return -1
        
        #ds = argDict.get("datasetname", "none")
        nb_results = int(argDict.get("nb_results", 10))
        
        classifiername = argDict.get("classifiername", "none")
        classifiername = classifiername.replace('_', ' ')

        output_dict = collections.OrderedDict()
        output_dict["meanscore"] = 0.0 # placeholder value
        output_dict["ranking"] = []
        
        # Test if the format is AXES-compliant
        isCorrectFormat = subprocess.call("\
        read -r SCORE VIDID MISC < \"%s/%s.scores\";\
        if [ \"$MISC\" == \"\" ]; then\
        	exit 1;\
        else\
        	exit 0;\
        fi" % (self.scoredir, classifiername), shell=True)

        f = open("%s/%s.scores" % (self.scoredir, classifiername), 'r')
        
        threshold = 0.0
        
        scoreTotal = 0.0
        
        for i in range(nb_results):
            try:
                l = f.readline().split()
                if l == []:
                    break
                
                if not isCorrectFormat:
                    line = l
                    
                    video = ' '.join(line[2:])
                    
                    shot = int(line[1])
                    score = float(line[0])

                    video = video.split('.')
                    video = video[:-1]
                    video = '.'.join(video)
                    
                    l = ['%f' % score, "/%s/s%09d" % (video, shot) ]
                    print l
                    
            except EOFError:
                return -1
            

                
            tmp_res = collections.OrderedDict()
            tmp_res["id"] = l[1]
            score = float(l[0])
            scoreTotal += score
            tmp_res["score"] = score
            output_dict["ranking"].append(tmp_res)
            
            if i <= 12:
                threshold = float(l[0]) - 0.001
            
        output_dict["status"] = "100"
            
        f.close()
        
        output_dict["meanscore"] = float(scoreTotal) / nb_results
        
        output_dict["stdscore"] = 0.0
        output_dict["threshold"] = threshold
        
        self.header_json()
        print>>self.wfile,json.dumps(output_dict)

    """
    def get_classifier_feedback(self, args):
        status = 0
        argDict = self.get_args(args, 3)
        if argDict == -1:
            argDict = self.get_args(args, 2)
            if argDict == -1:
                return -1
        
        ds = argDict.get("datasetname", "none")
        for dataset in self.base.datasets:
            if ds == dataset.name:
                status = 1
                classifier = argDict.get("classifiername", "none")
                i_Class = vd_utils.get_classifier_index_from_name(classifier)
                if i_Class in dataset.classifierList:
                    inputList = json.loads(argdict.get("shotlist", "none"))["list"]
                    for inputShot in inputList:
                        dataset, vidname, shotname = inputList.split('/')
                        iShot = int(shotname)
                        print "dataset %s, vidname %s, shot %d"%(dataset, vidname, iShot)
    """


    # parse argument list in the url and returns a dict (argName, argValue)
    def get_args(self, args, nbArgs):
        argDict = dict()
        if args == None:
        	return argDict
        
        argList = args.split('&')
        if len(argList) != nbArgs:
            #print>>self.wfile,"get_args error: incorrect nbArgs"
            return -1
        else:
            for arg in argList:
                equal = arg.find('=')
                argName = arg[:equal]
                argValue = arg[equal+1:]
                argDict[argName] = argValue
        return argDict

    # get scores from videos and sort them
    def get_scores(self, dataset, classifierIndex, nb_results):
        scores = {}
        vidnameList = [x.name for x in dataset.vidList]
        scores = scores.fromkeys(vidnameList)
        for vid in dataset.vidList:
            scores[vid.name] = [x.scores for x in vid.shots]
        sortedScores = collections.OrderedDict(sorted(scores.items(), key=lambda t: t[1], reverse=True))
        return sortedScores

    # load base from a pickle archive
    def load_base(self, fileName):
        dbFile = open(fileName,"r")
        self.base = pickle.load(dbFile)
        dbFile.close()


# header methods #

    # error 404 header
    def header_error_404(self):
        self.send_response(404)
        self.send_header("Content-type", "text/plain")
        self.end_headers()

    # html header
    def header_html(self): 
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.end_headers()

    # text header
    def header_text(self): 
        self.send_response(200)
        self.send_header("Content-type", "text/plain")
        self.end_headers()

    # json header
    def header_json(self):
        self.send_response(200)
        self.send_header("Content-type", "application/json")
        self.end_headers()

    # video header
    def header_video(self, extension="webm"):
        if extension in ["webm", "mp4", "mpeg", "ogg", "mkv", "avi"]:
            self.send_response(200)
            self.send_header("Content-type", "video/"+extension)
            self.end_headers()





##############################################################################
# main #

# help display
def usage():
    print "\n   usage: (python) event_recognition_server.py --scoredir DIR --port NUM [ --datasetname NAME ] \n"

# main function
def main():
    """ main function
    It attempts to launch a http server listening on port 80
    """
    datasetname = ""
    scoredir = ""
    port = 0

    # command line parsing
    args=sys.argv[1:]
    while args:
        a=args.pop(0)
        if a in ('-h','--help'):
            usage()
            sys.exit(0)
        elif a=='--datasetname':
            datasetname = args.pop(0)
        elif a=='--scoredir':
            scoredir = args.pop(0)
        elif a=='--port':
            port = int(args.pop(0))
        else:
            print "unknown option", a
            usage()
            sys.exit(1)
    
    if scoredir == "" or port == 0:
        print "Missing options"
        usage()
        sys.exit(1)

    try:
    	print '# Scores HTTP server - MED package'
    	print '# Initializing HTTP server..'
        server = BaseHTTPServer.HTTPServer(('localhost', port), lambda *b: EventRecognitionHandler(datasetname, scoredir, *b))
        print '# Started httpserver on port %d. Waiting for queries.' % port
    	print '#      curl http://localhost:%d/api_get_list_of_classifiers' % port
        print '#      curl http://localhost:%d/api_get_classifier_output?classifiername=EVENTNAME\&nb_results=10' % port
        print '#      curl http://localhost:%d/api_get_list_of_datasets' % port
        print ''
        
        server.serve_forever()
    except KeyboardInterrupt:
        print '# ^C received, shutting down server'
        server.socket.close()


if __name__ == '__main__':
    main()


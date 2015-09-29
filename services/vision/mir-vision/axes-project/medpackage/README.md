#   MED package (11 June 2015)
##   INRIA LEAR team (http://lear.inrialpes.fr)
     contact: Xavier Martin (firstname.lastname >AT< inria.fr)

What is this?
-------------
This is a package to learn and recognize event classes ("biking", "skiing"..) in videos.
It is streamlined such that adding and scoring new classes is a matter of finding
enough example videos.

The inner workings (descriptor extraction, classifier training) are the tools produced
and honed over the years for research and competition purposes at LEAR. This package
is an attempt at making those tools widely available and usable.

The package itself is a side-result of the AXES European project.
AXES is a joint effort between researchers, content providers and industrial partners,
to get a grasp on large multimedia data banks such as those kept by major broadcasters.
http://www.axes-project.eu/

What are the dependencies?
--------------------------
To run descriptor extraction, you will need the following items:

1. FFMpeg (devel)  http://ffmpeg.org/download.html
2. OpenCV (devel, FFMpeg interface enabled, depends on FFMpeg devel)  http://opencv.org/
3. yael library https://gforge.inria.fr/projects/yael/
4. Dense trajectories extractor http://lear.inrialpes.fr/people/wang/download/improved_trajectory_release.tar.gz

FFMpeg devel should be available in your distribution, the naming conventions vary.
You will probably need to compile OpenCV from source since you need the FFMpeg interface.
As for dense trajectories, there is no escaping it. :-)

For miscelleanous dependencies, use script "install_dependencies.sh".
Those include packages youtube-dl, elinks, and procmail.


How do I use it?
----------------
Setting up the package may require compiling FFMpeg or OpenCV from source.
However, when properly set up, the only requirement is knowing your way around a terminal.

You start with a concept you would like to recognize in videos, such as "cooking".
Then, gather some example videos to learn what cooking looks or sounds like.
The example videos fall in two categories: "positive" (baking cookies)
                                           "background" (climbing a telephone pole)

After gathering enough example videos, save them in the "./videos/" folder,
list their access path (relative to the ./videos/ folder) in two text files,
one for positive and one for background.

If you want to store your videos in a specific directory, export the following variable:

export COLLECTION_DIR="/path/to/my/collection"

And store your videos in "COLLECTION_DIR/videos". Alternatively, use the "--collection-dir" option available on all scripts.


When the video storage is sorted out, you are ready to initialize your event.

    ./event_init.sh "cooking" --positive-vids positive_cooking.txt --background-vids background_cooking.txt


This creates a subdirectory "./events/cooking/".
In case something goes awry check the usage string (use "-h" or "--help" on any script).
Now that your event is set up, you can extract the video descriptors.

    ./event_run_descriptor_extraction.sh "cooking"


Videos descriptors squeeze "what happens" in a video into a computer-friendly form.
Now that we have extracted descriptors from positive and background videos, it is time
to train our classifier. The classifier enables us to determine whether future videos
belong to the "positive" or the "background" category:

    ./event_run_training.sh "cooking"


The classifier is saved in your event's directory.
We are now ready to score new videos. First, generate their descriptors:

	./video_run_descriptor_extraction.sh  -h
    
Now you can compute a score against all the events you have trained:

	./video_run_scores.sh -h


Advanced users
--------------
By default, the videos are ranked using Heng Wang's "Dense Trajectories" (denseTrack).
Adding new channels and integrating them is quite easy.
The required files are:

- processing/compute_descriptors/YourChannel/YourChannel_descriptors.list
- processing/compute_descriptors/YourChannel/YourChannel_extraction.sh

"denseTrack" follows this format and is a good starting point.


Code quality
------------
The shell scripts that bind the package together have been written with "user-proofing"
in mind and thus should be quite stable. If you need to scale upwards, you are welcome to adapt them
to work in a distributed fashion or in a job scheduling system (check out OAR at http://oar.imag.fr).

I do apologize for the quick-and-dirty way I have repurposed some of the underlying modules,
particularly classifier training. Most of these modules were originally single-purpose scripts
that I had to make generic on a short notice. I hope to fix this in future revisions.

If you find a bug, have a suggestion or want to make a contribution, I welcome you with open arms.
I hope you enjoy playing around with this package and use it for awesome projects.
Do feel free to tell me all about it.


Thank you,

Xavier

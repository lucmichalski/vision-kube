CommonCrawl
===========

tutorials around http://commoncrawl.org/.

The interesting part is at [IPython-notebook-docker](https://github.com/rdhyee/CommonCrawlTutorials/tree/master/Experiments/IPython-notebook-docker),
which is a set of IPython Notebooks aimed at peforming basic CommonCrawl calculations.  Specifically, look at
[2014_08_Crawl.ipynb)](http://nbviewer.ipython.org/github/rdhyee/CommonCrawlTutorials/blob/master/Experiments/IPython-notebook-docker/2014_08_Crawl.ipynb).

One avenue of deployment is as a docker container
(see [Dockerfile](https://github.com/rdhyee/CommonCrawlTutorials/blob/master/Experiments/IPython-notebook-docker/Dockerfile)).  Notice how
the image extends the [ipython/docker-notebook](https://github.com/ipython/docker-notebook) images.

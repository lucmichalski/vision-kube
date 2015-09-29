socialmedia-abstractions
========================


<p>Social-Media Abstractions contain the necessary classes for mapping a number of social networks' wrappers to a single representation.</p>

<h2><u>Getting started</u></h2>
The project includes three set of packages, `eu.socialsensor.framework.streams`, `eu.socialsensor.framework.retrievers` and `eu.socialsensor.framework.abstractions` to be used for retrieving content from several social networks by calling their corresponing API's methods: <strong><a href="http://twitter4j.org/en/">Twitter</a></strong>, <strong><a href="http://restfb.com/">Facebook</a></strong>, <strong><a href="https://github.com/sachin-handiekar/jInstagram">Instagram</a></strong>, <strong>DailyMotion</strong>, <strong><a href="http://www.flickr.com/services/api/">Flickr</a></strong>, <strong>Twitpic</strong>, <strong><a href="https://developers.google.com/+/quickstart/java">Google+</a></strong>, <strong><a href="https://github.com/tumblr/jumblr">Tumblr</a></strong>, <strong>Vimeo</strong> and <strong><a href="https://developers.google.com/youtube/v3/">Youtube API</a></strong>. 

1. <strong>Streams</strong> are responsible for setting up the social stream according to the credentials that the user has provided and creating/closing the connection to the network in order to perform api calls. Specifically the individual streams that correspond to each social network implement the abstract class `eu.socialsensor.framework.streams.Stream`, whose basic operations are the following : 
    1. Opens the stream
    
            public abstract void open(StreamConfiguration config) throws StreamException;
    
    2. Starts the retrieval process with a given input 
    
            public Integer search(List<Feed> feeds) throws StreamException;
    
    3. Closes the stream after the retrieval process is completed
    
            public abstract void close() throws StreamException;

2. <strong>Retrievers</strong> are basicaly the wrappers to the social networks APIs, whose job is to perform calls according to an input feed they have received. For example, an input feed maybe a keyword or a set of keywords (<i>KeywordsFeed</i>), a social network user to follow (<i>SourceFeed</i>), or the coordinates of the location (<i>LocationFeed</i>).<strong> This only applies to the social networks' APIs that behave as polling consumers.</strong> Twitter retrieves content directly from the moment the connection is established in `eu.socialsensor.framework.streams.twitter.TwitterStream` class. Due to the fact that a social network retriever can process one input feed at a time, the retrieval process can work as a multi-threaded process, where each feed is handled as a separate thread. Each social network retriever implements the `eu.socialsensor.framework.retrievers.Retriever` interface, whose fundamental operations are described below : 
    1. Receives an input feed and checks its type (Keyword/Source/Location). In the end the method will return the number of items retrieved for that feed
    
            public List<Item> retrieve(Feed feed);
    
    2. Calls the appropriate retrieving method in regards to the input feed's type, which performs requests to the corresponding network according to its API methods. Please note that requests by location are not supported by most APIs. The retrieving methods are the following : 
    
            public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed) throws Exception;
            public List<Item> retrieveUserFeeds(SourceFeed feed) throws Exception;
            public List<Item> retrieveLocationFeeds(LocationFeed feed) throws Exception;
    

3. Finally, in <strong>Abstractions</strong> the information from the collected content is mapped to a single item representation in JSON format in order to be further handled more efficiently by the program or to be stored in the selected storages. 

<h2><u>Learning more</u></h2>

Abstractions project is dependent to other two SocialSensor projects : 

1. <a href="https://github.com/socialsensor/socialsensor-framework-common"> Socialsensor-framework-common</a> : The project contain main classes and interfaces to be used by other SocialSensor projects
2. <a href="https://github.com/socialsensor/socialsensor-framework-client"> Socialsensor-framework-client</a> : The wrappers for handling information in/from the supported databases (MongoDB,Solr,Lucene).

<h3><u>Contact for further details about the project</u></h3>

Manos Schinas (manosetro@iti.gr), Symeon Papadopoulos (papadop@iti.gr)

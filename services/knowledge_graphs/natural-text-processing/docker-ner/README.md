# docker-ner
Docker container for Stanford Named Entity Recognizer (NER)

Start the docker container
```
docker run -it mbartoli/ner /bin/bash
```

Start the NER server within the container
```
java -mx1000m -cp stanford-ner.jar edu.stanford.nlp.ie.NERServer \     
     -loadClassifier classifiers/english.muc.7class.distsim.crf.ser.gz  \
     -port 8080 -outputFormat inlineXML & 
```


Example usage with python within the container
```
import ner
tagger = ner.SocketNER(host='localhost', port=8080)
tagger.get_entities("Pomona College is located in Claremont, California")
```

```
Out[3]: 
{u'LOCATION': [u'Claremont', u'California'], u'ORGANIZATION': [u'Pomona College']}
```

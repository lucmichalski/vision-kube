/*
 * Copyright 2015 ROLLUS Loïc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrieval.server;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import retrieval.dist.*;
import retrieval.exception.CBIRException;
import retrieval.storage.Storage;
import retrieval.storage.exception.InternalServerException;
import retrieval.storage.exception.NoException;
import retrieval.storage.exception.WrongNumberOfTestsVectorsException;
import retrieval.storage.index.ResultSim;
import retrieval.utils.NetworkUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side Communication class between Client and server
 * with XML message and TCP/IP Socket.
 * This include NBT and similarities exchange
 * @author Rollus Loic
 */
public class RetrievalServerSocketXML implements ServerNetworkInterface {

    
    /**
     * Server which will carry request
     */
    private RetrievalServer server;
    /**
     * Socket for this server
     */
    private ServerSocket serverSocket = null;
    /**
     * Logger
     */
    private static final Logger logger = Logger.getLogger(RetrievalServerSocketXML.class);

    /**
     * Constructor for a central server vs server communication
     * @param server Server which will carry request
     * @param port Port for request
     * @throws InternalServerException Exception during the start of the request thread
     */
    public RetrievalServerSocketXML(RetrievalServer server,int port)
            throws InternalServerException {
        logger.info("MultiServerSocketXML: start on " + port);

        this.server = server;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            logger.error(e);
            throw new InternalServerException(e.getMessage());
        }

    }

    public void close()
    {
        try {serverSocket.close();} catch(Exception e){logger.warn("close: "+e);}
    }

    /**
     * Wait for a search request from central server
     */
    public void waitForRequest() {

        while (true) {
            Socket clientSocket = null;
            try {

                logger.debug("waitForRequest: wait on socket");
                if(serverSocket.isClosed()) {
                    break;
                }
                clientSocket = serverSocket.accept();

                logger.debug("waitForRequest: connexion on " + clientSocket.getInetAddress().getHostAddress());

                NewClientThread ncw = new NewClientThread(server,clientSocket);
                ncw.start();

            } catch (Exception e) {
                logger.fatal(e);
            }
        }
    }
}

/**
 * A thread which will carry A single request from central server
 * @author Rollus Loic
 */
class NewClientThread extends Thread {

    /**
     * Socket from client (central server)
     */
    private Socket client;
    /**
     * Server which will carry the request
     */
    private RetrievalServer multiServer;
    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(NewClientThread.class);

    /**
     * Constructor for a search thread
     * @param server Server which will carry the request
     * @param client Socket from client (central server)
     * @param tg Thread Group which will be group of this thread
     * @param maxK Max number of similar picture for a request
     */
    NewClientThread(RetrievalServer multiServer, Socket client) {
        this.client = client;
        this.multiServer = multiServer;
    }

    @Override public void run() {

        try {

            logger.debug("run: read message");
            //receive message that ask NBT from server
            Document requestXML = NetworkUtils.readXmlFromSocket(client);

            logger.debug("run: requestXML message");
            if(requestXML.getRootElement().getAttributeValue("type").equals("SEARCH1")) {
                takeSearchRequest(requestXML);
            }
            else if(requestXML.getRootElement().getAttributeValue("type").equals("INDEX")) {
                BufferedImage image = NetworkUtils.readBufferedImageFromSocket(client);
                takeIndexRequest(requestXML,image);
            }
            else if(requestXML.getRootElement().getAttributeValue("type").equals("DELETE")) {
                takeDeleteRequest(requestXML);
            }
            else if(requestXML.getRootElement().getAttributeValue("type").equals("PURGE")) {
                takePurgeRequest();
            }
            else if(requestXML.getRootElement().getAttributeValue("type").equals("INFOS")) {
                takeStatsRequest( requestXML );
            }
            else if(requestXML.getRootElement().getAttributeValue("type").equals("STORAGES")) {
                takeStoragesRequest( requestXML );
            }            
            else {
                throw new NotValidMessageXMLException("Command "+requestXML.getRootElement().getAttributeValue("type")+ " not valid!");
            }

        } catch (WrongNumberOfTestsVectorsException e) {
            logger.error(e);
            MessageError msg = new MessageError(e);
            NetworkUtils.writeXmlToSocketWithoutException(client, msg.toXML());
        } catch (NotValidMessageXMLException e) {
            logger.error("waitForRequest:NotValidMessageException" + e);
            NetworkUtils.writeXmlToSocketWithoutException(client, new MessageError("9999", "Fatal error").toXML());
        } catch (Exception e) {
            logger.error("waitForRequest:" + e);
            e.printStackTrace();
            NetworkUtils.writeXmlToSocketWithoutException(client, new MessageError("9999", "Fatal error").toXML());

        }
    }

    private void takeSearchRequest(Document xml) throws NotValidMessageXMLException, IOException,WrongNumberOfTestsVectorsException, Exception {
            logger.debug("takeSearchRequest");
            MultiServerMessageNBT msgAskNBT = new MultiServerMessageNBT(xml);
            
            logger.debug("run: get NBT");
            //search all NBT and fill this message
            Map<String,List<ConcurrentHashMap<String, Long>>> visualWords  = multiServer.getNBT(msgAskNBT.getVisualWordsByTestVectorServer().get("#all#"),msgAskNBT.getContainers());
            msgAskNBT.setVisualWordsByTestVectorServer(visualWords);
            logger.debug("run: write response NBT");
            //response to central server
            NetworkUtils.writeXmlToSocket(client, msgAskNBT.toXML());
            logger.debug("run: read similarities message");
            //read second message that ask similarities from central server
            MultiServerMessageSimilarities msg2 = new MultiServerMessageSimilarities(NetworkUtils.readXmlFromSocket(client));
            Map<String,List<ConcurrentHashMap<String, RequestPictureVisualWord>>> vw = msg2.getVisualWord();
            int Niq = msg2.getNiq();
            int k = msg2.getK();
            //send results messages
            logger.debug("run: write response results");
            Map<String,List<ResultSim>> results = multiServer.getPicturesSimilarities(vw, Niq, k,msg2.getContainers());
            //Map<String,Long> serverSize = multiServer.getStoragesSize();

            //logger.debug("### Server size:"+serverSize);
            MultiServerMessageResults msg3 = new MultiServerMessageResults(results);
//            logger.debug("### Server size:"+msg3.getNumberOfPicturesInIndex());
            NetworkUtils.writeXmlToSocket(client, msg3.toXML());

            client.close();
    }


    private void takeIndexRequest(Document xml, BufferedImage image) throws NotValidMessageXMLException, IOException,WrongNumberOfTestsVectorsException, Exception {
        logger.debug("takeIndexRequest");
        MultiServerMessageIndex msgIndex = new MultiServerMessageIndex(xml);
        MultiServerMessageIndexResults msgResult;
        if(msgIndex.async) {
            msgResult = takeIndexAsynchroneRequest(msgIndex,image);
        } else {
            msgResult = takeIndexSynchroneRequest(msgIndex,image);
        }
        NetworkUtils.writeXmlToSocket(client, msgResult.toXML());
        client.close();
    }
    
    private MultiServerMessageIndexResults takeIndexAsynchroneRequest(MultiServerMessageIndex msgIndex, BufferedImage image) throws Exception {
            logger.debug("index asyncrhone");
            //sort pictures by server key
            Long pictureId = msgIndex.getId();
            Map<String,Map<Long, CBIRException>> allServerResult = new HashMap<String,Map<Long, CBIRException>>();
            
            Storage server;
            logger.debug("msgIndex.getStorage()="+msgIndex.getStorage() + "=>"+msgIndex.getStorage().equals(RetrievalServer.EQUITABLY));
            if(msgIndex.getStorage().equals(RetrievalServer.EQUITABLY)) {
                server = multiServer.getNextStorage();
            } else {
                server = multiServer.getStorage(msgIndex.getStorage());
                if(server==null) {
                    multiServer.createStorage(msgIndex.getStorage());
                    server = multiServer.getStorage(msgIndex.getStorage());
                }
            }
            
            logger.debug("Picture " + pictureId + " add on queue index "+server.getStorageName());
            Map<Long, CBIRException> serverResult = allServerResult.get(server.getStorageName());
            if(serverResult==null) {
                serverResult = new HashMap<Long, CBIRException>();
            }            

            try {
                pictureId = server.addToIndexQueue(image, pictureId, msgIndex.getProperties());
                serverResult.put(pictureId, new NoException());
            } catch(CBIRException e) {
                serverResult.put(pictureId, e);
            } catch(Exception e) {
                serverResult.put(pictureId, new CBIRException(e.toString()));
            }

            allServerResult.put(msgIndex.getStorage(),serverResult);
   
            logger.debug("allServerResult="+allServerResult);
            return new MultiServerMessageIndexResults(allServerResult);        
    }
    
    private MultiServerMessageIndexResults takeIndexSynchroneRequest(MultiServerMessageIndex msgIndex, BufferedImage image) throws Exception {
            logger.debug("index synchrone");
            System.out.println("msgIndex.getStorage()="+msgIndex.getStorage());
            Storage server;
            if(msgIndex.getStorage().equals(RetrievalServer.EQUITABLY)) {
                server = multiServer.getNextStorage();
            } else {
                server = multiServer.getStorage(msgIndex.getStorage());
                if(server==null) {
                    multiServer.createStorage(msgIndex.getStorage());
                    server = multiServer.getStorage(msgIndex.getStorage());
                }
            }
            logger.debug(msgIndex);
            logger.debug("ID " + msgIndex.getId() + " will be in storage " + msgIndex.getStorage());
            logger.debug("Image has properties " + msgIndex.getProperties());

            Map<String,Map<Long, CBIRException>> mapResult = new TreeMap<String,Map<Long, CBIRException>>();
            Map<Long, CBIRException> map = new HashMap<Long, CBIRException>();            
            try {
                Long id = server.indexPicture(image,msgIndex.getId(),msgIndex.getProperties());
                map.put(id, new NoException());
            } catch(CBIRException e) {
                map.put(msgIndex.getId(), e);
            } 
            mapResult.put(msgIndex.getStorage(), map);
      
            return new MultiServerMessageIndexResults(mapResult);        
    }
    
    
    private void takeDeleteRequest(Document xml) throws NotValidMessageXMLException, IOException,WrongNumberOfTestsVectorsException, Exception {
        logger.debug("takeDeleteRequest");
        MultiServerMessageDelete msgIndex = new MultiServerMessageDelete(xml);
        List<Long> ids = msgIndex.getIds();
        multiServer.delete(ids);
        Document doc = new Document();
        doc.setRootElement(new Element(NetworkUtils.NORESPONSE));
        NetworkUtils.writeXmlToSocket(client, doc);
    }
    private void takePurgeRequest() throws NotValidMessageXMLException, IOException,WrongNumberOfTestsVectorsException, Exception {
        logger.debug("takePurgeRequest");
        multiServer.purge();
    }

     private void takeStatsRequest(Document xml) throws NotValidMessageXMLException, IOException,WrongNumberOfTestsVectorsException, Exception {
         MultiServerMessageInfos msgRequest = new MultiServerMessageInfos(xml);
         //String storage = msgRequest.getStorage();
         logger.info("Get infos for storage = "+msgRequest.getStorage());
         MultiServerMessageInfos msgResult = new MultiServerMessageInfos(multiServer.getInfos(msgRequest.getStorage()),msgRequest.getStorage());
         NetworkUtils.writeXmlToSocket(client, msgResult.toXML());
    }
     
     private void takeStoragesRequest(Document xml) throws NotValidMessageXMLException, IOException,WrongNumberOfTestsVectorsException, Exception {
         //String storage = msgRequest.getStorage();
         MultiServerMessageStorages msgResult = new MultiServerMessageStorages(multiServer.getStoragesSize());
         NetworkUtils.writeXmlToSocket(client, msgResult.toXML());
    }     
}


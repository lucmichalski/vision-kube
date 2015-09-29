package retrieval.storage;

import org.junit.Test;
import retrieval.TestUtils;
import retrieval.config.ConfigClient;
import retrieval.config.ConfigServer;
import retrieval.exception.CBIRException;
import retrieval.storage.exception.AlreadyIndexedException;
import retrieval.storage.exception.NoException;
import retrieval.testvector.TestVectorListClient;
import retrieval.testvector.generator.TestVectorReading;
import retrieval.utils.FileUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 *
 * @author lrollus
 */
public abstract class StorageTestAbstract extends TestUtils {
 
    Storage storage = null;
    ConfigServer config = null;    
    
    public StorageTestAbstract() {
    }
    
    void testStorageStart() throws Exception {
        storage.start();
        storage.stop();
    }

    /**
     * Test of getNumberOfItem method, of class Server.
     */
    @Test
    public void testServerGetNumberOfItem() throws Exception {
        System.out.println("testServerGetNumberOfItem");
        assertEquals(0, storage.getNumberOfItem());  
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,LOCALPICTURE1MAP);
        assertEquals(1, storage.getNumberOfItem()); 
        List<Long> ids = new ArrayList<Long>();
        ids.add(id);
        storage.deletePictures(ids);
        assertEquals(0, storage.getNumberOfItem()); 
    }

    /**
     * Test of indexPicture method, of class Server.
     */
    @Test
    public void testServerIndexPicture() throws Exception {
        System.out.println("testServerIndexPicture");
        assertEquals(0, storage.getNumberOfItem());  
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,LOCALPICTURE1MAP);
        assertEquals(1, storage.getNumberOfItem()); 
        Map<String,String> properties = storage.getProperties(id);
        assertEquals(TestUtils.BASE_NUMBER_OF_PROPERTIES+2, properties.size());
        assertEquals("CROP1", properties.get("name")); 
    }
    
    /**
     * Test of indexPicture method, of class Server.
     */
    @Test
    public void testServerIndexPictureWithId() throws Exception {
        System.out.println("testServerIndexPicture");
        assertEquals(0, storage.getNumberOfItem());  
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),5l,null);
        assertEquals(1, storage.getNumberOfItem()); 
        Map<String,String> properties = storage.getProperties(id);
        assertEquals(TestUtils.BASE_NUMBER_OF_PROPERTIES,properties.size());
        assertEquals(new Long(5l), id); 
    }   
    
     /**
     * Test of indexPicture method, of class Server.
     */
    @Test
    public void testServerIndexPictureWithAlreadyExist() throws Exception {
        System.out.println("testServerIndexPicture");
        assertEquals(0, storage.getNumberOfItem());  
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),5l,null);
        assertEquals(1, storage.getNumberOfItem()); 
        Map<String,String> properties = storage.getProperties(id);
        assertEquals(TestUtils.BASE_NUMBER_OF_PROPERTIES,properties.size());
        assertEquals(new Long(5l), id); 
    }      
    
    /**
     * Test of addToIndexQueue method, of class Server.
     */
    @Test(expected = AlreadyIndexedException.class)  
    public void testServerIndexAlreadyExist() throws Exception {
        System.out.println("testServerAddToIndexQueue");
        storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1), 10l, null);
        storage.indexPicture(FileUtils.readPicture(LOCALPICTURE2), 10l,null);
    }        
    
    @Test
    public void testServerDeletePicture() throws Exception {
        System.out.println("testServerGetNumberOfItem");
        assertEquals(0, storage.getNumberOfItem());  
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,LOCALPICTURE1MAP);
        assertEquals(1, storage.getNumberOfItem()); 
        List<Long> ids = new ArrayList<Long>();
        ids.add(id);
        storage.deletePictures(ids);
        assertEquals(0, storage.getNumberOfItem()); 
        assertNotNull(storage.getProperties(id)); 
        //assertEquals(TestUtils.BASE_NUMBER_OF_PROPERTIES+2,storage.getProperties(id).size());
        assertEquals(false, storage.isPictureInIndex(id)); 
    }
    
    @Test
    public void testServerGetProperties() throws Exception {
        System.out.println("testServerGetNumberOfItem");
        assertEquals(0, storage.getNumberOfItem());  
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,LOCALPICTURE1MAP);
        assertNotNull(storage.getProperties(id)); 
        Map<String,String> properties = storage.getProperties(id); 
        for(String key : LOCALPICTURE1MAP.keySet()) {
            assertEquals(true, properties.containsKey(key)); 
            assertEquals(LOCALPICTURE1MAP.get(key), properties.get(key)); 
        }
    }    
 
    /**
     * Test of indexPurge method, of class Server.
     */
    @Test
    public void testServerIndexPurge() throws Exception {
        System.out.println("testServerIndexPurge");
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,null);

        storage.deletePicture(id);
        //check if picture is in this file
        assertEquals(1,storage.getNumberOfPicturesToPurge());
        storage.purgeIndex();
        assertEquals(0,storage.getNumberOfPicturesToPurge());
    }
       
    /**
     * Test of isPictureCorrectlyRemovedFromIndex method, of class Server.
     */
    @Test
    public void testServerIsPictureCorrectlyRemovedFromIndex() throws Exception {
        System.out.println("testServerIsPictureCorrectlyRemovedFromIndex");
        assertEquals(true,storage.isPictureCorrectlyRemovedFromIndex(15l));
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),15l,null);
        assertEquals(false,storage.isPictureCorrectlyRemovedFromIndex(id));
        storage.deletePicture(id);
        //check if picture is in this file
        assertEquals(false,storage.isPictureCorrectlyRemovedFromIndex(id));
        storage.purgeIndex();
        assertEquals(true,storage.isPictureCorrectlyRemovedFromIndex(id));
    }

    /**
     * Test of deleteIndex method, of class Server.
     */
    @Test
    public void testServerDeleteIndex() throws Exception {
        System.out.println("testServerDeleteIndex");
        storage.deleteIndex();
    }

    /**
     * Test of getIndexPath method, of class Server.
     */
    @Test
    public void testServerGetIndexPath() {
        System.out.println("testServerGetIndexPath");
        assertEquals(config.getIndexPath(), storage.getIndexPath());
    }

    /**
     * Test of printIndex method, of class Server.
     */
    @Test
    public void testServerPrintIndex() {
        System.out.println("testServerPrintIndex");
        storage.printIndex();
    }
    
    /**
     * Test of getInfo method, of class Server.
     */
    @Test
    public void testServerGetList() throws Exception{
        System.out.println("testServerGetList");
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,null); 
        System.out.println("qsdfg="+id);
        System.out.println("storage.getAllPictures()="+storage.getAllPictures());
        assertEquals(true,storage.getAllPictures().contains(id));
    }   
    
    /**
     * Test of getInfo method, of class Server.
     */
    @Test
    public void testServerGetInfo() throws Exception{
        System.out.println("testServerGetInfo");
        Long id = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,null);
        HashMap<Long,CBIRException> toCheck = new HashMap<Long,CBIRException>();
        toCheck.put(id, null);
        assertEquals(NoException.CODE,storage.getInfo(toCheck).get(id).getCode());
    }    

    /**
     * Test of getAllPicturesMap method, of class Server.
     */
    @Test
    public void testServerGetAllPicturesMap() throws Exception{
        System.out.println("testServerGetAllPicturesMap");
        Long id1 = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),5l,LOCALPICTURE1MAP);
        Long id2 = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),null,null);
        
        Map<Long,Map<String,String>> map = storage.getAllPicturesMap();
        assertEquals(2,map.size());
        assertTrue(map.containsKey(id1));
        assertNotNull(map.get(id1));
        assertEquals("CROP1",map.get(id1).get("name"));
        
        assertTrue(map.containsKey(id2));
        assertNotNull(map.get(id2));
        assertEquals(TestUtils.BASE_NUMBER_OF_PROPERTIES,map.get(id2).size());
    }

    /**
     * Test of isPictureInIndex method, of class Server.
     */
    @Test
    public void testServerIsPictureInIndex() throws Exception{
        System.out.println("testServerIsPictureInIndex");
        Long id1 = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),5l,LOCALPICTURE1MAP);
        assertEquals(true, storage.isPictureInIndex(id1));      
        assertEquals(false, storage.isPictureInIndex(99L));
       
    }


    /**
     * Test of getNBT method, of class Server.
     */
    @Test
    public void testServerSearch() throws Exception {
        System.out.println("testServerSearch");        
        Long id1 = storage.indexPicture(FileUtils.readPicture(LOCALPICTURE1),5l,LOCALPICTURE1MAP);  
        TestVectorListClient buildVW = TestVectorReading.readClient(config.getVectorPath(),new ConfigClient("testdata/ConfigClient.prop"));
        List<ConcurrentHashMap<String,Long>> vw = buildVW.generateVisualWordFromPicture(ImageIO.read(new File(LOCALPICTURE1)), id1, config.getNumberOfPatch(), config.getResizeMethod(), config.getSizeOfPatchResizeWidth(), config.getSizeOfPatchResizeHeight());
        List<ConcurrentHashMap<String,Long>> result = storage.getNBT(vw);
    }
    
   
}

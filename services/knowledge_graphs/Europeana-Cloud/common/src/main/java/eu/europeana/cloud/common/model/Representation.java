package eu.europeana.cloud.common.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Representation of a record in specific version.
 */
@XmlRootElement
public class Representation {

    /**
     * Identifier (cloud id) of a record this object is representation of.
     */
    private String cloudId;

    /**
     * Representation Name of this representation.
     */
    private String representationName;

    /**
     * Identifier of a version of this representation.
     */
    private String version;

    /**
     * Uri to the history of all versions of this representation.
     */
    private URI allVersionsUri;

    /**
     * Self uri.
     */
    private URI uri;

    /**
     * Data provider of this version of representation.
     */
    private String dataProvider;

    /**
     * A list of files which constitute this representation.
     */
    private List<File> files = new ArrayList<File>(0);

    /**
     * If this is temporary representation version: date of this object creation; If this is persistent representation
     * version: date of making this object persistent.
     */
    private Date creationDate;

    /**
     * Indicator whether this is persistent representation version (true) or temporary (false).
     */
    private boolean persistent;


    /**
     * Creates a new instance of this class.
     */
    public Representation() {
        super();
    }


    /**
     * Creates a new instance of this class.
     * 
     * @param cloudId
     * @param representationName
     * @param version
     * @param allVersionsUri
     * @param uri
     * @param dataProvider
     * @param files
     * @param persistent
     * @param creationDate
     */
    public Representation(String cloudId, String representationName, String version, URI allVersionsUri, URI uri,
            String dataProvider, List<File> files, boolean persistent, Date creationDate) {
        super();
        this.cloudId = cloudId;
        this.representationName = representationName;
        this.version = version;
        this.allVersionsUri = allVersionsUri;
        this.uri = uri;
        this.dataProvider = dataProvider;
        this.files = files;
        this.persistent = persistent;
        this.creationDate = creationDate != null ? creationDate : null;
    }


    /**
     * Creates a new instance of this class.
     * 
     * @param representation
     */
    public Representation(final Representation representation) {
        this(representation.getCloudId(), representation.getRepresentationName(), representation.getVersion(),
                representation.getAllVersionsUri(), representation.getUri(), representation.getDataProvider(),
                cloneFiles(representation), representation.isPersistent(), representation.getCreationDate());
    }


    private static List<File> cloneFiles(Representation representation) {
        List<File> files = new ArrayList<>(representation.getFiles().size());
        for (File file : representation.getFiles()) {
            files.add(new File(file));
        }
        return files;
    }


    public String getCloudId() {
        return cloudId;
    }


    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }


    public String getRepresentationName() {
        return representationName;
    }


    public void setRepresentationName(String representationName) {
        this.representationName = representationName;
    }


    public String getVersion() {
        return version;
    }


    public void setVersion(String version) {
        this.version = version;
    }


    public String getDataProvider() {
        return dataProvider;
    }


    public void setDataProvider(String dataProvider) {
        this.dataProvider = dataProvider;
    }


    public List<File> getFiles() {
        return files;
    }


    public void setFiles(List<File> files) {
        this.files = files;
    }


    public boolean isPersistent() {
        return persistent;
    }


    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }


    public URI getAllVersionsUri() {
        return allVersionsUri;
    }


    public void setAllVersionsUri(URI allVersionsUri) {
        this.allVersionsUri = allVersionsUri;
    }


    public URI getUri() {
        return uri;
    }


    public void setUri(URI selfUri) {
        this.uri = selfUri;
    }


    public Date getCreationDate() {
        return creationDate;
    }


    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate != null ? creationDate : null;
    }

    /**
     * This method is required for @PostFilter (Spring ACL) at RepresentationsResource.getRepresentations() 
     */
    public String getId(){
        return getACLId();
    }
    
    private String getACLId(){
        return this.getCloudId()+"/"+this.getRepresentationName()+"/"+this.getVersion();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.cloudId);
        hash = 37 * hash + Objects.hashCode(this.representationName);
        hash = 37 * hash + Objects.hashCode(this.version);
        hash = 37 * hash + Objects.hashCode(this.dataProvider);
        hash = 37 * hash + Objects.hashCode(this.files);
        hash = 37 * hash + Objects.hashCode(this.creationDate);
        hash = 37 * hash + (this.persistent ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Representation other = (Representation) obj;
        if (!Objects.equals(this.cloudId, other.cloudId)) {
            return false;
        }
        if (!Objects.equals(this.representationName, other.representationName)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        if (!Objects.equals(this.dataProvider, other.dataProvider)) {
            return false;
        }
        if (!Objects.equals(this.files, other.files)) {
            return false;
        }
        if (!Objects.equals(this.creationDate, other.creationDate)) {
            return false;
        }
        if (this.persistent != other.persistent) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Representation{" + "cloudId=" + cloudId + ", representationName=" + representationName + ", version="
                + version + ", dataProvider=" + dataProvider + ", files=" + files + ", creationDate=" + creationDate
                + ", persistent=" + persistent + '}';
    }
}

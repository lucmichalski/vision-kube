#ifndef ARTOS_SYNSETIMAGE_H
#define ARTOS_SYNSETIMAGE_H

#include <string>
#include <vector>
#include <ios>
#include "ffld/JPEGImage.h"
#include "ffld/Rectangle.h"

namespace ARTOS
{

/**
* Stores information (image data and bounding box annotations) about an image in a synset of
* of an image repository.
*
* @author Bjoern Barz <bjoern.barz@uni-jena.de>
*/
class SynsetImage
{

public:

    /**
    * Constructs an incomplete SynsetImage instance, not usable in any way.
    */
    SynsetImage() : m_repoDir(""), m_synsetId(""), m_filename(""), m_imgLoaded(false), m_bboxesLoaded(false) { };

    /**
    * @param[in] repoDirectory Path to the repository directory.
    *
    * @param[in] synsetId ID of the synset the image belongs to.
    *
    * @param[in] filename The filename of the image, with or without extension (it will be ignored).
    *
    * @param[in] img If different from NULL, the data of the image will be copied from the image
    *                given by this argument, so that it doesn't have to be loaded again later on.
    */
    SynsetImage(const std::string & repoDirectory, const std::string & synsetId,
                const std::string & filename, const FFLD::JPEGImage * img = NULL);

    /**
    * @return Returns the path to the repository directory.
    */
    std::string getRepoDirectory() const { return this->m_repoDir; };
    
    /**
    * @return Returns the ID of the synset this image belongs to.
    */
    std::string getSynsetId() const { return this->m_synsetId; };
    
    /**
    * @return Returns the filename of this image, without the ".JPEG" extension.
    */
    std::string getFilename() const { return this->m_filename; };

    /**
    * @return Returns the full path to this image or an empty string if the image could not be found.
    */
    std::string getPath() const;
    
    /**
    * Checks if all parameters of this SynsetImage instance have been set (i. e. repository directory,
    * synset ID and filename).
    *
    * @return Returns true if this is a valid SynsetImage instance, but false if one of the necessary parameters is empty.
    */
    bool valid() const { return (!this->m_repoDir.empty() && !this->m_synsetId.empty() && !this->m_filename.empty()); };
    
    /**
    * Returns the image itself. If the image isn't already in memory, it will be loaded from disk.
    *
    * @return The image as JPEGImage object.
    */
    FFLD::JPEGImage & getImage()
    {
        if (!this->m_imgLoaded && this->m_img.empty())
            this->loadImage();
        return this->m_img;
    };
    
    /**
    * Loads bounding box annotations for this image into the bboxes vector if available.
    *
    * The bounding boxes given in the file will be rescaled according to the size of this image.
    *
    * Successive calls to this function will do nothing.
    *
    * @return Returns true if bounding box annotations are available for this image, otherwise false.
    */
    bool loadBoundingBoxes();
    
    /**
    * Cuts out the parts of the images defined by the bounding box annotations.
    * 
    * @param[out] samples Vector that the sub-images inside of the bounding boxes will be appended to.
    */
    void getSamplesFromBoundingBoxes(std::vector< FFLD::JPEGImage > & samples);
    
    /**
    * Vector with rectangular bounding boxes around instances of the object category associated with
    * the synset of this image.
    *
    * @note Bounding box annotations need to be loaded before using loadBoundingBoxes().
    */
    std::vector< FFLD::Rectangle > bboxes;


protected:

    std::string m_repoDir; /**< The path to the repository directory. */
    std::string m_synsetId; /**< The ID of the synset this image belongs to. */
    std::string m_filename; /**< The filename of this image. */
    FFLD::JPEGImage m_img; /**< Image data (may be an empty image if not loaded yet). */
    bool m_imgLoaded; /**< Specifies whether an attempt to load the image has been made. */
    bool m_bboxesLoaded; /**< Specifies if bounding box annotations have already been loaded for this image. */

    /**
    * Loads the actual image data from disk into the m_img field.
    */
    void loadImage();

};

}

#endif

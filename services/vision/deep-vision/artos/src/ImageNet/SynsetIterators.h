#ifndef ARTOS_IMAGEITERATOR_H
#define ARTOS_IMAGEITERATOR_H

#include <iterator>
#include <fstream>
#include "SynsetImage.h"
#include "TarExtractor.h"

namespace ARTOS
{


struct Synset; // forward declaration


/**
* Iterator over synsets in an image repository.
*
* Can be used the following way, for example:
*
*     for (SynsetIterator it = repo.getSynsetIterator(); it.ready(); ++it)
*         Synset s = *it;
*
* @note it++ is no allowable operation on this iterator, since it is not copyable.
*
* @author Bjoern Barz <bjoern.barz@uni-jena.de>
*/
class SynsetIterator : public std::iterator<std::input_iterator_tag, Synset, int>
{

public:

    /**
    * @param[in] aRepoDirectory The path to the repository directory.
    */
    SynsetIterator(const std::string & aRepoDirectory);
    
    /**
    * Copies the parameters, but not the state (!) of another SynsetIterator.
    *
    * @param[in] other Another SynsetIterator whose parameters are to be applied to the new instance.
    */
    SynsetIterator(const SynsetIterator & other);
    
    /**
    * Moves the iterator to the next synset in the repository.
    *
    * @return The iterator itself after applying the operation.
    */
    SynsetIterator & operator++();
    
    /**
    * Returns a Synset object initialized with the ID and description of the current synset.
    *
    * @return Synset instance
    */
    Synset operator*() const;

    /**
    * Determines if the last move forward operation was successful.
    *
    * @return Returns true if the synset list file could be opened and the iterator
    *         points to a synset at the moment.
    */
    bool ready() const { return (this->m_listFile.is_open() && this->m_listFile.good()); };
    
    /**
    * @return Returns the current iterator position, i. e. the number of synsets already skipped.
    */
    unsigned int pos() const { return this->m_pos - 1; };
    
    /**
    * Allows the use of `(unsigned int) it` instead of `it.pos()`.
    *
    * @return Returns the current iterator position, i. e. the number of synsets already skipped.
    */
    operator unsigned int() const { return this->m_pos - 1; };
    
    /**
    * Allows the use of `(int) it` instead of `it.pos()`.
    *
    * @return Returns the current iterator position, i. e. the number of synsets already skipped.
    */
    operator int() const { return this->m_pos - 1; };
    
    /**
    * @return Returns the path to the repository directory.
    */
    std::string getRepoDirectory() const { return this->m_repoDir; };


protected:

    std::string m_repoDir; /**< Path to the repository directory. */
    std::ifstream m_listFile; /**< File stream associated with the synset list file. */
    std::string m_lastLine; /**< Last line read from the list file. */
    size_t m_pos; /**< Index of the current synset. */


private:

    SynsetIterator & operator=(const SynsetIterator);

};



/**
* Abstract base class for iterators over images of an image repository.
*
* Such iterators can be used the following way, for example:
*
*     for (ImageIterator it = synset.getImageIterator(); it.ready() && (int) it < 10; ++it)
*         SynsetImage img = *it;
*
* @note it++ is no allowable operation on these iterators, since they are not copyable.
*
* @author Bjoern Barz <bjoern.barz@uni-jena.de>
*/
class ImageIterator : public std::iterator<std::input_iterator_tag, SynsetImage, int>
{

public:

    /**
    * @param[in] aRepoDirectory The path to the repository directory.
    */
    ImageIterator(const std::string & aRepoDirectory) : m_repoDir(aRepoDirectory), m_pos(0) { };
    
    /**
    * Moves the iterator to the next image.
    *
    * @return The iterator itself after applying the operation.
    */
    virtual ImageIterator & operator++() =0;
    
    /**
    * Returns a SynsetImage object initialized with the image at the current position.
    *
    * @return SynsetImage instance
    *
    * @note Dereferencing an iterator of this class is expensive.
    *       Store the obtained SynsetImage instance in a variable if possible.
    */
    virtual SynsetImage operator*() =0;
    
    /**
    * Resets the iterator to it's initial state.
    */
    virtual void rewind() =0;

    /**
    * Determines if the initialization or the last move forward operation was successful.
    *
    * @return Returns true if the next dereferencing will be successful on this iterator.
    */
    virtual bool ready() const =0;

    /**
    * @return Returns the current iterator position, i. e. the number of images already skipped.
    */
    virtual unsigned int pos() const { return this->m_pos - 1; };
    
    /**
    * Allows the use of `(unsigned int) it` instead of `it.pos()`.
    *
    * @return Returns the current iterator position, i. e. the number of images already skipped.
    */
    virtual operator unsigned int() const { return this->m_pos - 1; };
    
    /**
    * Allows the use of `(int) it` instead of `it.pos()`.
    *
    * @return Returns the current iterator position, i. e. the number of images already skipped.
    */
    virtual operator int() const { return this->m_pos - 1; };
    
    /**
    * @return Returns the path to the repository directory.
    */
    virtual std::string getRepoDirectory() const { return this->m_repoDir; };


protected:

    std::string m_repoDir; /**< Path to the repository directory. */
    unsigned int m_pos; /**< Number of already skipped images. */

};



/**
* Iterator over images in a synset.
*
* Can be used the following way, for example:
*
*     for (SynsetImageIterator it = synset.getImageIterator(); it.ready() && (int) it < 10; ++it)
*         SynsetImage img = *it;
*
* @note it++ is no allowable operation on this iterator, since it is not copyable.
*
* @author Bjoern Barz <bjoern.barz@uni-jena.de>
*/
class SynsetImageIterator : public ImageIterator
{

public:

    /**
    * @param[in] aRepoDirectory The path to the repository directory.
    *
    * @param[in] aSynsetId The ID of the synset.
    *
    * @param[in] bboxRequired If set to true, the iterator walks over the contents of the
    *                         annotations archive, so that only images which bounding boxes are
    *                         available for will be returned. If set to false, the iterator walks
    *                         over the actual image archive instead, regardless of bounding boxes.
    */
    SynsetImageIterator(const std::string & aRepoDirectory, const std::string & aSynsetId, const bool & bboxRequired = false);

    /**
    * Copies the parameters, but not the state (!) of another SynsetImageIterator.
    *
    * @param[in] other Another SynsetImageIterator whose parameters are to be applied to the new instance.
    */
    SynsetImageIterator(const SynsetImageIterator & other);
    
    /**
    * Moves the iterator to the next image in the synset.
    *
    * @return The iterator itself after applying the operation.
    */
    virtual SynsetImageIterator & operator++();
    
    /**
    * Returns a SynsetImage object initialized with the image at the current position.
    *
    * @return SynsetImage instance
    *
    * @note Dereferencing an iterator of this class is expensive.
    *       Store the obtained SynsetImage instance in a variable if possible.
    */
    virtual SynsetImage operator*();

    /**
    * Resets the iterator to it's initial state.
    */
    virtual void rewind();

    /**
    * Determines if the last move forward operation was successful.
    *
    * @return Returns true if the synset archive could be opened and the iterator points to an
    *         image at the moment.
    */
    virtual bool ready() const { return (this->m_tar.isOpen() && this->m_tar.good() && this->m_lastFileName != ""); };
    
    /**
    * @return Returns the ID of the synset this iterator is associated with.
    */
    virtual std::string getSynsetId() const { return this->m_synsetId; };


protected:

    std::string m_synsetId; /**< ID of the synset. */
    bool m_bboxMode; /**< Specifies if only images which bounding box annotations are available for are taken into account. */
    std::string m_lastFileName; /**< Name of the last file. */
    unsigned int m_lastFileIndex; /**< Index of the last file in the Tar archive. */
    std::ifstream::pos_type m_lastFileOffset; /**< Offset of the last file relative to the beginning of the Tar archive. */
    TarExtractor m_tar; /**< TarExtractor instance associated with the synset archive. */


private:

    SynsetImageIterator & operator=(const SynsetImageIterator);

};



/**
* Iterator over images from different synsets of an image repository.
*
* The number of images taken from each synset can be specified. After that number has been extracted
* from the first synset, the iterator will proceed with the second synset and so on. When the last
* synset has been processed, the next bunch of images will be taken from the first. If a synset does
* not contain any more images, the first images will be taken from it again. Thus, this iterator is endless.
*
* Can be used the following way, for example:
*
*     for (MixedImageIterator it = repo.getMixedIterator(); it.ready() && (int) it < 10; ++it)
*         SynsetImage img = *it;
*
* @note it++ is no allowable operation on this iterator, since it is not copyable.
*
* @author Bjoern Barz <bjoern.barz@uni-jena.de>
*/
class MixedImageIterator : public ImageIterator
{

public:

    /**
    * @param[in] aRepoDirectory The path to the repository directory.
    *
    * @param[in] aPerSynset Number of images taken from each synset in a row.
    */
    MixedImageIterator(const std::string & aRepoDirectory, const unsigned int & aPerSynset);

    /**
    * Copies the parameters, but not the state (!) of another MixedImageIterator.
    *
    * @param[in] other Another MixedImageIterator whose parameters are to be applied to the new instance.
    */
    MixedImageIterator(const MixedImageIterator & other);
    
    /**
    * Moves the iterator to the next image in the synset.
    *
    * @return The iterator itself after applying the operation.
    */
    virtual MixedImageIterator & operator++();
    
    /**
    * Returns a SynsetImage object initialized with the image at the current position.
    *
    * @return SynsetImage instance
    *
    * @note Dereferencing an iterator of this class is expensive.
    *       Store the obtained SynsetImage instance in a variable if possible.
    */
    virtual SynsetImage operator*();
    
    /**
    * Resets the iterator to it's initial state.
    */
    virtual void rewind();
    
    /**
    * Extracts the image at the current position directly to disk.
    *
    * @param[in] outDirectory The directory where the image is to be stored (using it's original filename).
    *                         Existing files with the same name will be overwritten.
    *
    * @return Returns the filename/basename of the extracted image or an empty string on failure.
    */
    virtual std::string extract(const std::string & outDirectory);
    
    /**
    * Determines if this iterator is ready to be used.
    * 
    * @return Returns true if at least one synset exists, otherwise false.
    */
    virtual bool ready() const { return (this->m_synsets.size() > 0 && this->m_tar.isOpen()); };


protected:

    std::vector<std::string> m_synsets; /**< List of synset IDs. */
    size_t m_currentSynset; /**< The index of the currently opened synset in the m_synsets vector. */
    std::string m_lastSynset; /**< The ID of the synset which the last file belongs to. */
    std::string m_lastFileName; /**< Name of the last file. */
    std::ifstream::pos_type m_lastFileOffset; /**< Offset of the last file relative to the beginning of the Tar archive. */
    unsigned int m_lastFileIndex; /**< Index of the last file in it's Tar archive. */
    unsigned int m_perSynset; /**< Number of images taken from each synset in a row. */
    unsigned int m_posCurrent; /**< Number of images extracted or skipped from the currently opened synset. */
    unsigned int m_run; /**< Number of full runs over all synsets (incremented whenever the last synset has been processed). */
    bool m_foundAny; /**< Determines if any synset has been found after the first run through the list. */
    TarExtractor m_tar; /**< TarExtractor instance associated with the synset archive. */


private:

    MixedImageIterator & operator=(const MixedImageIterator);
    
    /**
    * Called from constructors to initialize the iterator.
    */
    void init();
    
    /**
    * Moves on to the next synset.
    */
    void nextSynset();

};

}

#endif
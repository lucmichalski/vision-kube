/**
 * 
 */
package org.openimaj.image.processing.edges;

import gnu.trove.list.array.TFloatArrayList;

import org.openimaj.citation.annotation.Reference;
import org.openimaj.citation.annotation.ReferenceType;
import org.openimaj.image.FImage;
import org.openimaj.image.processor.SinglebandImageProcessor;

/**
 * 	Implementations of the SUSAN edge detection algorithm. The default
 * 	processor uses the simple version; there are static methods for the
 * 	other versions which are pretty slow. However, the circular version allows
 * 	you to detect "fat" lines which would otherwise be detected as two
 * 	separate lines.
 * 
 *  @author David Dupplaw (dpd@ecs.soton.ac.uk)
 *	
 *	@created 18 Jun 2012
 */
@Reference(
		author = { "S.M. Smith" }, 
		title = "A new class of corner finder",
		type = ReferenceType.Article,
		url = "http://users.fmrib.ox.ac.uk/~steve/susan/susan/node4.html",
		year = "1992",
		booktitle = "Proc. 3rd British Machine Vision Conference",
		pages = "139-148"
)
public class SUSANEdgeDetector implements
        SinglebandImageProcessor<Float, FImage>
{
	/**
	 * 	An enumerator of different SUSAN edge detector types	
	 * 
	 *  @author David Dupplaw (dpd@ecs.soton.ac.uk)
	 *	
	 *	@created 18 Jun 2012
	 */
	private enum SUSANDetector
	{
		/**
		 * 	The simple, fast SUSAN detector
		 */
		SIMPLE
		{
			@Override
            public FImage process( FImage img )
            {
				return SUSANEdgeDetector.simpleSusan( img, threshold, nmax );
            }
		},
		/**
		 * 	The smooth SUSAN detector
		 */
		SMOOTH
		{
			@Override
            public FImage process( FImage img )
            {
				return SUSANEdgeDetector.smoothSusan( img, threshold, nmax );
            }
		},
		/**
		 * 	The smooth, circular detector
		 */
		CIRCULAR
		{
			@Override
            public FImage process( FImage img )
            {
				return SUSANEdgeDetector.smoothCircularSusan( img, threshold, nmax, radius );
            }			
		}
		;
		
		protected double threshold = 0.08;
		protected double nmax = 9;
		protected double radius = 3.4;
		
		public abstract FImage process( FImage img );
	}
	
	/** The SUSAN detector in use */
	private SUSANDetector susan = SUSANDetector.SIMPLE;

	/**
	 * 	Default constructor that instantiates a simple SUSAN edge detector
	 * 	with threshold 0.08 and global threshold weighting 9.
	 */
	public SUSANEdgeDetector()
    {
		this.susan = SUSANDetector.SIMPLE;
    }
	
	/**
	 * 	@param s The susan detector to use
	 *  @param threshold The threshold to use
	 *  @param nmax The global threshold weighting
	 */
	public SUSANEdgeDetector( SUSANDetector s, double threshold, 
			double nmax )
    {
		this.susan = s;
		susan.threshold = threshold;
		susan.nmax = nmax;
    }

	/**
	 * 	@param s The susan detector to use
	 *  @param threshold The threshold to use
	 *  @param nmax The global threshold weighting
	 *  @param radius The radius of the circular susan
	 */
	public SUSANEdgeDetector( SUSANDetector s, double threshold, 
			double nmax, double radius )
    {
		this.susan = s;
		susan.threshold = threshold;
		susan.nmax = nmax;
		susan.radius = radius;
    }
	
	/**
	 *	{@inheritDoc}
	 * 	@see org.openimaj.image.processor.ImageProcessor#processImage(org.openimaj.image.Image)
	 */
	@Override
    public void processImage( FImage image )
    {
		image.internalAssign( susan.process( image ) );
    }
	
	/**
	 * 	Performs the simple SUSAN edge detection.
	 * 	
	 *  @param img The image to find edges in
	 *  @param thresh The threshold
	 *  @param nmax The global threshold weighting
	 *  @return Edge image
	 */
	public static FImage simpleSusan( FImage img, double thresh, double nmax )
	{
	    FImage area = new FImage( img.getWidth(), img.getHeight() );
	    
	    double globalThresh = (3.0 * nmax) / 4.0;
	    
        for( int y = 1; y < img.getHeight()-1; y++ )
	    {
    	    for( int x = 1; x < img.getWidth()-1; x++ )
	        {
	            double a = 0;
	            for( int x1 = x-1; x1 < x+2; x1++ )
	            {
	                for( int y1 = y-1; y1 < y+2; y1++ )
	                {
	                    if( Math.abs( img.getPixel(x1,y1) - img.getPixel(x,y) ) < thresh )
	                        a++;
	                }
	            }
	            
	            if( a < globalThresh )
	                area.setPixel( x, y, (float)(globalThresh - a) );
	        }
	    }
	    
	    return area;
	}
	
	/**
	 * 	Performs the simple SUSAN edge detection.
	 * 	
	 *  @param img The image to find edges in
	 *  @param thresh The threshold
	 *  @param nmax The global threshold weighting
	 *  @return Edge image
	 */
	public static FImage smoothSusan( FImage img, double thresh, double nmax )
	{
	    FImage area = new FImage( img.getWidth(), img.getHeight() );
	    
	    double globalThresh = (3.0 * nmax) / 4.0;
	    
        for( int y = 1; y < img.getHeight()-1; y++ )
	    {
    	    for( int x = 1; x < img.getWidth()-1; x++ )
	        {
	            double a = 0;
	            for( int x1 = x-1; x1 < x+2; x1++ )
	            {
	                for( int y1 = y-1; y1 < y+2; y1++ )
	                {
                        a += Math.exp( 
                        		-Math.pow( 
                        			Math.abs( img.getPixel(x1,y1) - 
                        					img.getPixel(x,y) )
                        				/thresh, 6 ) );
	                }
	            }
	            
	            if( a < globalThresh )
	                area.setPixel( x, y, (float)(globalThresh - a) );
	        }
	    }
	    
	    return area;
	}

	/**
	 * 	Performs the simple SUSAN edge detection.
	 * 	
	 *  @param img The image to find edges in
	 *  @param thresh The threshold
	 *  @param nmax The global threshold weighting
	 *  @param radius The radius of the circle (try 3.4)
	 *  @return Edge image
	 */
	public static FImage smoothCircularSusan( FImage img, double thresh, double nmax, double radius )
	{
	    FImage area   = new FImage( img.getWidth(), img.getHeight() );
	    double globalThresh = (3.0 * nmax) / 4.0;
	    
	    int r = (int)Math.ceil(radius);
        for( int y = r; y < img.getHeight()-r; y++ )
	    {
    	    for( int x = r; x < img.getWidth()-r; x++ )
	        {
    	    	float[] pixelValues = getPixelsInCircle( x, y, radius, img );
    	    	double a = 0;
    	    	for( float f : pixelValues )    	    		
                        a += Math.exp( 
                        		-Math.pow( 
                        			Math.abs( f - 
                        					img.getPixel(x,y) )
                        				/thresh, 6 ) );
	            
	            if( a < globalThresh )
	                area.setPixel( x, y, (float)(globalThresh - a) );
	        }
	    }
	    
	    return area;
	}

	/**
	 * 	Returns the values of pixels within a circle centres at cx, cy in the
	 * 	image img, with a radius r.
	 *  @param cx The centre of the circle's x coordinate
	 *  @param cy The centre of the circle's y coordinate
	 *  @param r The radius of the circle
	 *  @param img The image from which to take pixels
	 *  @return A list of pixel values
	 */
	private static float[] getPixelsInCircle( int cx, int cy, double r, FImage img )
	{
		TFloatArrayList f = new TFloatArrayList();
	    for( int i  = (int)Math.ceil(cx-r); i < (int)Math.ceil(cx+r); i++ )
	    {
	        double ri = Math.sqrt( r*r-(i-cx)*(i-cx) );
	        for( int j = (int)Math.ceil(cy-ri); j < (int)Math.ceil(cy+ri); j++ )
	        {
	        	f.add( img.getPixel(i,j) );
	        }
	    }
	    return f.toArray();
	}
}

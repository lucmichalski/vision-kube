

package gr.iti.mklab.sfc.streams;

/**
 * An exception that can be thrown during stream related operations
 *
 */
public class StreamException extends Exception{
	
	private static final long serialVersionUID = 4359142708795406111L;

	public StreamException(Exception e) {
		super(e);
	}
	
	public StreamException(String message){
		super(message);
	}
	
	public StreamException(String message, Exception e) {
		super(message,e);
	}

}

package eu.europeana.cloud.client.aas.rest;

import eu.europeana.cloud.common.exceptions.GenericException;

/**
 * Generic Cloud Exception
 * 
 * @author Yorgos.Mamakis@ kb.nl
 * @since Dec 17, 2013
 */
public class  CloudException  extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8451384934113123019L;

	/**
	 * Creates a new instance of this class with the wrapped cloud Exception
	 * @param message
	 * @param t The cloud exception to wrap
	 */
	public <T extends GenericException> CloudException(String message,T  t) {
		super(message,t);
	}

}

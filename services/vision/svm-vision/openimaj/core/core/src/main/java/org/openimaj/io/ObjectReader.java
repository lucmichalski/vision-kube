/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.io;

import java.io.IOException;

/**
 * Interface for classes capable of reading objects.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 * 
 * @param <T>
 *            Type of object being read.
 * @param <SOURCE>
 *            The type of the source of data for the object being read
 */
public interface ObjectReader<T, SOURCE> {
	/**
	 * Read an object from the source
	 * 
	 * @param source
	 *            the source
	 * @return the object
	 * @throws IOException
	 *             if an error occurs
	 */
	public T read(SOURCE source) throws IOException;

	/**
	 * Returns true if the stream can be read, or false otherwise.
	 * <p>
	 * This method is not normally called directly; rather,
	 * {@link IOUtils#canRead(ObjectReader, Object, String)} should be used
	 * instead.
	 * 
	 * @see IOUtils#canRead(ObjectReader, Object, String)
	 * 
	 * @param source
	 *            the data source
	 * @param name
	 *            the name of the file behind the stream (can be null).
	 * @return true if this {@link ObjectReader} can read the stream; false
	 *         otherwise.
	 */
	public boolean canRead(SOURCE source, String name);
}

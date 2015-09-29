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
package org.openimaj.lsh.sketch;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.openimaj.util.hash.HashFunction;
import org.openimaj.util.hash.HashFunctionFactory;
import org.openimaj.util.sketch.Sketcher;

/**
 * A {@link Sketcher} that produces bit-string sketches encoded as a
 * {@link BitSet}. Only the least-significant bit of each hash function will be
 * appended to the final sketch. The length of the output array will be computed
 * such that the bit from each hash function is contained.
 *
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 *
 * @param <OBJECT>
 *            Type of object being sketched
 */
public class BitSetLSHSketcher<OBJECT> implements Sketcher<OBJECT, BitSet> {
	List<HashFunction<OBJECT>> hashFunctions;

	/**
	 * Construct with the given functions.
	 *
	 * @param functions
	 *            the underlying hash functions.
	 */
	public BitSetLSHSketcher(List<HashFunction<OBJECT>> functions) {
		this.hashFunctions = functions;
	}

	/**
	 * Construct with the given functions.
	 *
	 * @param first
	 *            the first function
	 * @param remainder
	 *            the remainder of the functions
	 */
	@SafeVarargs
	public BitSetLSHSketcher(HashFunction<OBJECT> first, HashFunction<OBJECT>... remainder) {
		this.hashFunctions = new ArrayList<HashFunction<OBJECT>>();
		this.hashFunctions.add(first);

		for (final HashFunction<OBJECT> r : remainder)
			this.hashFunctions.add(r);
	}

	/**
	 * Construct with the factory which is used to produce the required number
	 * of functions.
	 *
	 * @param factory
	 *            the factory to use to produce the underlying hash functions.
	 * @param nFuncs
	 *            the number of functions to create for the composition
	 */
	public BitSetLSHSketcher(HashFunctionFactory<OBJECT> factory, int nFuncs) {
		this.hashFunctions = new ArrayList<HashFunction<OBJECT>>();

		for (int i = 0; i < nFuncs; i++)
			hashFunctions.add(factory.create());
	}

	@Override
	public BitSet createSketch(OBJECT input) {
		final int nbits = bitLength();
		final BitSet sketch = new BitSet(nbits);

		for (int k = 0; k < nbits; k++) {
			final int hash = hashFunctions.get(k).computeHashCode(input);

			sketch.set(k, ((hash & 1) == 1) ? true : false);
		}

		return sketch;
	}

	/**
	 * Get the length of the sketch in bits.
	 *
	 * @return the number of bits in the sketch
	 */
	public int bitLength() {
		return hashFunctions.size();
	}
}

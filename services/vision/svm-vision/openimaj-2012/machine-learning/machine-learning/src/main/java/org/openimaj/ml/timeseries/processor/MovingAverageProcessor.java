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
package org.openimaj.ml.timeseries.processor;

import org.apache.commons.math.stat.StatUtils;
import org.openimaj.ml.timeseries.processor.interpolation.LinearInterpolationProcessor;
import org.openimaj.ml.timeseries.processor.interpolation.TimeSeriesInterpolation;
import org.openimaj.ml.timeseries.series.DoubleTimeSeries;

/**
 * Calculates a moving average over a specified window in the past such that  
 * 
 * data[t_n] = sum^{m}_{i=1}{data[t_{n-i}}
 * 
 * This processor returns a value for each time in the underlying time series. 
 * For sensible results, consider interpolating a consistent time span using an {@link LinearInterpolationProcessor}
 * followed by this processor.
 * 
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class MovingAverageProcessor implements TimeSeriesProcessor<double[],Double,DoubleTimeSeries>
{
	
	
	
	private long length;

	/**
	 * @see TimeSeriesInterpolation#TimeSeriesInterpolation(long[])
	 * @param length the length of the window placed ending at t_n
	 */
	public MovingAverageProcessor(long length) {
		this.length = length;
	}

	@Override
	public void process(DoubleTimeSeries series) {
		long[] times = series.getTimes();
		double[] data = series.getData();
		int size = series.size();
		for (int i = size-1; i >= 0; i--) {
			long latest = times[i];
			long earliest = latest - length;
			DoubleTimeSeries spanoftime = series.get(earliest, latest);
			data[i] = StatUtils.mean(spanoftime.getData());
		}
	}
}

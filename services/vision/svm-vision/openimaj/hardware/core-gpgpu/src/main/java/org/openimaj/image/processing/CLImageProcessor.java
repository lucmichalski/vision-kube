package org.openimaj.image.processing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.openimaj.image.CLImageConversion;
import org.openimaj.image.Image;
import org.openimaj.image.processor.ImageProcessor;

import com.nativelibs4java.opencl.CLBuildException;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLImage2D;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLPlatform.DeviceFeature;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;

/**
 * Base {@link ImageProcessor} for GPGPU accelerated processing.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 *
 * @param <I> Type of {@link Image} being processed
 */
public class CLImageProcessor<I extends Image<?, I>> implements ImageProcessor<I> {
	protected CLContext context;
	protected CLKernel kernel;

	/**
	 * Construct with the given OpenCL program
	 * @param program the OpenCL program
	 */
	public CLImageProcessor(CLProgram program) {
		try {
			this.context = JavaCL.createBestContext(DeviceFeature.GPU);
			this.kernel = program.createKernels()[0];
		} catch (CLBuildException e) {
			//fallback to OpenCL on the CPU
			this.context = JavaCL.createBestContext(DeviceFeature.CPU);
			this.kernel = program.createKernels()[0];			
		}
	}

	/**
	 * Construct with the given OpenCL program source, given in
	 * the form of {@link String}s.
	 * 
	 * @param programSrcs the source of the program
	 */
	public CLImageProcessor(String... programSrcs) {
		CLProgram program;
		try {
			this.context = JavaCL.createBestContext(DeviceFeature.GPU);
			program = context.createProgram(programSrcs);
			this.kernel = program.createKernels()[0];
		} catch (CLBuildException e) {
			//fallback to OpenCL on the CPU
			this.context = JavaCL.createBestContext(DeviceFeature.CPU);
			program = context.createProgram(programSrcs);
			this.kernel = program.createKernels()[0];
		}
	}

	/**
	 * Construct with the program sourcecode at the given URL. 
	 * @param srcUrl the url
	 * @throws IOException
	 */
	public CLImageProcessor(URL srcUrl) throws IOException {
		this(IOUtils.toString(srcUrl));
	}

	/**
	 * Construct by reading the program source from a stream
	 * @param src the source stream
	 * @throws IOException
	 */
	public CLImageProcessor(InputStream src) throws IOException {
		this(IOUtils.toString(src));
	}

	/**
	 * Construct with the given OpenCL program
	 * @param context the OpenCL context to use
	 * @param program the OpenCL program
	 */
	public CLImageProcessor(CLContext context, CLProgram program) {
		this.context = context;
		this.kernel = program.createKernels()[0];
	}

	/**
	 * Construct with the given OpenCL program source, given in
	 * the form of {@link String}s.
	 * @param context the OpenCL context to use
	 * 
	 * @param programSrcs the source of the program
	 */
	public CLImageProcessor(CLContext context, String... programSrcs) {
		this.context = context;
		CLProgram program = context.createProgram(programSrcs);
		this.kernel = program.createKernels()[0];
	}
	
	/**
	 * Construct with the given OpenCL kernel
	 * @param kernel the OpenCL kernel to use
	 */
	public CLImageProcessor(CLKernel kernel) {
		this.context = kernel.getProgram().getContext();
		this.kernel = kernel;
	}

	/**
	 * Construct with the program sourcecode at the given URL. 
	 * @param context the OpenCL context to use
	 * @param srcUrl the url
	 * @throws IOException
	 */
	public CLImageProcessor(CLContext context, URL srcUrl) throws IOException {
		this(context, IOUtils.toString(srcUrl));
	}

	/**
	 * Construct by reading the program source from a stream
	 * @param context the OpenCL context to use
	 * @param src the source stream
	 * @throws IOException
	 */
	public CLImageProcessor(CLContext context, InputStream src) throws IOException {
		this(context, IOUtils.toString(src));
	}

	@Override
	public void processImage(I image) {
		CLQueue queue = context.createDefaultQueue();

		CLImage2D in = CLImageConversion.convert(context, image);
		CLImage2D out = context.createImage2D(CLMem.Usage.Output, in.getFormat(), in.getWidth(), in.getHeight());
		
		kernel.setArgs(in, out);
		CLEvent evt = kernel.enqueueNDRange(queue, new int[] {(int) in.getWidth(), (int) in.getHeight()});

		CLImageConversion.convert(queue, evt, out, image);
		
		in.release();
		out.release();
		queue.release();
	}
}

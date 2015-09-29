/**
 * Copyright (c) 2012, The University of Southampton and the individual contributors.
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
package org.openimaj.twitter.collection;

import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.openimaj.io.FileUtils;
import org.openimaj.twitter.GeneralJSON;
import org.openimaj.twitter.USMFStatus;
import org.openimaj.util.list.AbstractFileBackedList;


public class FileTwitterStatusList<T extends USMFStatus> extends AbstractFileBackedList<T> implements TwitterStatusList<T> {

	private Class<? extends GeneralJSON> seedClass=null;
	
	protected FileTwitterStatusList(int size, File file, String charset, Class<T> clazz) {
		super(size, false, 0, -1, file, clazz,charset);
	}
	protected FileTwitterStatusList(int size, File file, String charset, Class<T> clazz, Class<? extends GeneralJSON> seedClass) {
		super(size, false, 0, -1, file, clazz,charset);
		this.seedClass = seedClass;
	}
	
	protected FileTwitterStatusList(int size, File file,Class<T> clazz) {
		super(size, false, 0, -1, file, clazz);
	}
	

	/**
	 * 
	 */
	private static final long serialVersionUID = -785707085718120105L;

	@Override
	public void writeASCII(PrintWriter out) throws IOException {
		for (int i = 0; i < this.size; i++) {
			if(i != 0) out.println();
			this.get(i).writeASCII(out);
		}
	}
	
	@Override
	protected T newElementInstance() {
		if(seedClass == null)
			return (T) new USMFStatus();
		else
			return (T) new USMFStatus(seedClass);
	}
	
	@Override
	public String asciiHeader() {
		return "";
	}
	
	public static FileTwitterStatusList<USMFStatus> read(File f) throws IOException {
		int size = FileUtils.countLines(f);
		return new FileTwitterStatusList<USMFStatus>(size, f,USMFStatus.class);
	}
	
	public static FileTwitterStatusList<USMFStatus>  read(File f,String charset) throws IOException {
		int size = FileUtils.countLines(f);
		return new FileTwitterStatusList<USMFStatus>(size, f,charset,USMFStatus.class);
	}
	
	public static FileTwitterStatusList<USMFStatus>  read(File f,int size) throws IOException {
		return new FileTwitterStatusList<USMFStatus>(size, f,USMFStatus.class);
	}
	
	public static FileTwitterStatusList<USMFStatus>  read(File f,String charset,int size) throws IOException {
		return new FileTwitterStatusList<USMFStatus>(size, f,charset,USMFStatus.class);
	}
	
	public static FileTwitterStatusList<USMFStatus> read(File f, String charset, Class<? extends GeneralJSON> generalJSON){
		int size = FileUtils.countLines(f);
		return new FileTwitterStatusList<USMFStatus>(size, f,charset,USMFStatus.class,generalJSON);
	}
	

	@Override
	public void writeBinary(DataOutput out) throws IOException {
		throw new UnsupportedOperationException();
		
	}

	@Override
	public byte[] binaryHeader() {
		throw new UnsupportedOperationException();
	}

	
	
	@Override
	protected AbstractFileBackedList<T> newInstance(int newSize, boolean isBinary, int newHeaderLength, int recordLength, File file) {
		return new FileTwitterStatusList<T>(newSize,file,this.charset,this.clz);
	}

}

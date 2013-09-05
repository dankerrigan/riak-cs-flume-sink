package com.basho.proserv.flume;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

public class TempFileManager {
	private final String filePrefix;
	private final String fileSuffix;
	private final String fileExtension;
	private final File tempLocation;
	
	HashMap<String, TempFile> tempFiles = new LinkedHashMap<String, TempFile>();
	
	public TempFileManager(String filePrefix, String fileSuffix, String fileExtension) {
		this(filePrefix, fileSuffix, fileExtension, Files.createTempDir());
	}
	
	public TempFileManager(String filePrefix, String fileSuffix, String fileExtension, File tempLocation) {
		Preconditions.checkNotNull(filePrefix);
		Preconditions.checkNotNull(fileSuffix);
		Preconditions.checkNotNull(fileExtension);
		Preconditions.checkArgument(tempLocation.exists());
		
		this.filePrefix = filePrefix;
		this.fileSuffix = fileSuffix;
		this.fileExtension = fileExtension;
		this.tempLocation = tempLocation;
		
	}
	
	public int writeData(byte[] data, String identifier) throws IOException {
		TempFile tempFile = getTempFile(identifier);
		
		tempFile.outputStream().write(data);
		
		return data.length;
	}
	
	public List<File> listAndPurgeClosedFiles() {
		List<File> files = new ArrayList<File>();
		
		for (String id : this.tempFiles.keySet()) {
			TempFile tempFile = this.tempFiles.get(id);
			if (tempFile.isClosed()) {
				files.add(tempFile.file());
				this.tempFiles.remove(id);
			}
		}
		
		return files;
	}
	
	public void closeIdentifier(String identifier) throws IOException {
		TempFile tempFile = tempFiles.get(identifier);
		if (tempFile.isClosed()) {
			throw new IllegalStateException(
					String.format("Temp file %s is already closed!", tempFile.file().getAbsolutePath()));
		}
		tempFile.close();
	}
	
	private TempFile getTempFile(String identifier) throws IOException {
		if (tempFiles.containsKey(identifier)) {
			return tempFiles.get(identifier);
		} else {
			String tempFilename = String.format("%s/%s%s%s.%s", this.tempLocation.getAbsolutePath(), this.filePrefix, identifier, this.fileSuffix, this.fileExtension);
			
			File tempFile = new File(tempFilename);
			OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
			
			TempFile newTempFile = new TempFile(tempFile, outputStream); 
			
			this.tempFiles.put(identifier, newTempFile);
			
			return newTempFile;
		}
	}
	
	private class TempFile {
		private final File tempFile;
		private final OutputStream outputStream;
		private boolean closed = false;
		
		public TempFile(final File file, final OutputStream outputStream) {
			this.tempFile = file;
			this.outputStream = outputStream;
			
		}
		
		public File file() {
			return this.tempFile;
		}
		
		public OutputStream outputStream() {
			return this.outputStream;
		}
		
		public void close() throws IOException {
			this.outputStream.flush();
			this.outputStream.close();
			
			this.closed = true;
		}
		
		public boolean isClosed() {
			return this.closed;
		}
	}
}

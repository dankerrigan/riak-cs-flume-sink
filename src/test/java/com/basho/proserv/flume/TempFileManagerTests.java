package com.basho.proserv.flume;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import com.google.common.io.Files;

import junit.framework.TestCase;

public class TempFileManagerTests extends TestCase {
	
	public void testWriteData() throws Exception {
		String identifier = "1234567890";
		String data = "1234567890";
		
		
		TempFileManager tfm = new TempFileManager("", "", "", Files.createTempDir());
		
		tfm.writeData(data.getBytes(), identifier);
		
		tfm.closeIdentifier(identifier);
		
		List<File> files = tfm.listAndPurgeClosedFiles();
		
		assertTrue(files.size() == 1);
		
		InputStream is = new FileInputStream(files.get(0));
		
		byte[] readData = new byte[data.length()];
		
		is.read(readData, 0, data.length());
		
		is.close();
		
		String readDataString = new String(readData);
		
		assertTrue(data.compareTo(readDataString) == 0);
	}
}

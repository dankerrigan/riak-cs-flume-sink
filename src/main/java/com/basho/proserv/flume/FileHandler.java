package com.basho.proserv.flume;

import java.io.File;
import java.io.InputStream;

public interface FileHandler {
	boolean put(String bucketName, File file);
	
	boolean put(String bucketName, String key, InputStream inputStream, long length);
	
	InputStream get(String bucket, File file);
	
	InputStream get(String bucket, String key);
}

package com.basho.proserv.flume;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class RiakCSFileHandler implements FileHandler {
	
	private static final Logger LOG = LoggerFactory.getLogger(RiakCSFileHandler.class);

	private final String accessKey;
	private final String secretKey;
	private final Jets3tProperties jets3Properties;
	
	private S3Service s3Service;
	
	public static void create() {
		System.out.println("Whut did you say?");
	}
	
//	public static void create(String accessKey, String secretKey, boolean useHTTPS, String proxyHost, int proxyPort) {
//		System.out.println("Whut did you say?");
////		return new RiakCSFileHandler(accessKey, secretKey, useHTTPS, proxyHost, proxyPort);
//	}
	
	public RiakCSFileHandler(String accessKey, String secretKey, boolean useHTTPS, String proxyHost, int proxyPort) {
		System.out.println("what????");
		Preconditions.checkNotNull(accessKey);
		Preconditions.checkNotNull(secretKey);
		
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		
		if (!proxyHost.isEmpty() || proxyPort > 0) {
			this.jets3Properties = new Jets3tProperties();
			this.jets3Properties.setProperty("s3service.https-only", useHTTPS ? "true" : "false");
			this.jets3Properties.setProperty("httpclient.proxy-autodetect", "false");
			this.jets3Properties.setProperty("httpclient.proxy-host", proxyHost);
			this.jets3Properties.setProperty("httpclient.proxy-port", ((Integer)proxyPort).toString());
			
			this.jets3Properties.setProperty("httpclient.retry-max", ((Integer)0).toString());
			this.jets3Properties.setProperty("httpclient.connection-timeout-ms", ((Integer)1000).toString());
			this.jets3Properties.setProperty("httpclient.socket-timeout-ms", ((Integer)1000).toString());
		} else {
			this.jets3Properties = null;
		}
		
		boolean success = initializeS3();
		if (!success) {
			LOG.error("Could not initialize connection to CS");
			throw new RuntimeException("Could not initialize connection to CS");
		}
	}
	
	public boolean initializeS3() {
		AWSCredentials credentials = new AWSCredentials(accessKey, secretKey);
		
		try {
			if (this.jets3Properties == null) {
				this.s3Service = new RestS3Service(credentials);
			} else {
				this.s3Service = new RestS3Service(credentials, "RiakCSSink/1.0", null, this.jets3Properties);
			}
			
			this.s3Service.isAuthenticatedConnection();
		} catch (S3ServiceException e) {
			LOG.error(e.getMessage());
			return false;
		}
		return true;
	}
	
	public boolean put(String bucketName, File file) {
		try {
			return put(bucketName, file.getName(), new BufferedInputStream(new FileInputStream(file)), file.length());
		} catch (FileNotFoundException e) {
			LOG.error(e.getMessage());
		}
		return false;
	}

	public boolean put(String bucketName, String key, InputStream inputStream, long length) {
		S3Bucket bucket = new S3Bucket(bucketName); 
		
		S3Object object = new S3Object(key);
		object.setDataInputStream(inputStream);
		object.setContentType("binary/octet-stream");
		object.setContentLength(length);
		
		
		S3Object uploaded = null;
		try {
			uploaded = this.s3Service.putObject(bucket, object);
			inputStream.close();
		} catch (S3ServiceException e) {
			LOG.error(e.getMessage());
			return false;
		} catch (RuntimeException e) {
			LOG.error(e.getMessage());
			return false;
		} catch (IOException e) {
			LOG.error("Couldn't close file event file", e);
		}
		return true;
	}

	public InputStream get(String bucketName, File file) {
		return get(bucketName, file.getName());
	}

	public InputStream get(String bucketName, String key) {
		
		try {
			S3Object object = this.s3Service.getObject(bucketName, key);
			
			return object.getDataInputStream();
			
		} catch (S3ServiceException e) {
			LOG.error(e.getMessage());
		} catch (ServiceException e) {
			LOG.error("Unable to retrieve inputstream from Object", e);
		}
		
		return null;
	}

}

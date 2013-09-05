package com.basho.proserv.flume;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.flume.Event;
import org.apache.flume.lifecycle.LifecycleAware;
import org.apache.flume.lifecycle.LifecycleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

public class RiakCSWriter implements LifecycleAware {
	private static final Logger LOG = LoggerFactory.getLogger(RiakCSWriter.class); 

	private static final long QUEUE_POLL_WAIT = 100;
	
	private static final int EVENT_QUEUE_SIZE = 1000;
	private static final int TEMP_FILE_QUEUE_SIZE = 1000;
	private static final int CS_UPLOAD_THREADS = 1;
	private static final int CS_THREAD_WAIT = 30000;

	private final String filePrefix;
	private final String fileSuffix;
	private final String fileExtension;
	private final String bucketName;
	private final String accessKey;
	private final String secretKey;
	private final boolean useHTTPS;
	private final String proxyHost;
	private final int proxyPort;
	
	private final long rollInterval;
	private final long rollSize;
	private final long rollCount;
	
	private final ExecutorService executor = Executors.newCachedThreadPool();
//	private final ExecutorService executor = Executors.newFixedThreadPool(10);
	
	private BlockingQueue<Event> eventQueue = new ArrayBlockingQueue<Event>(EVENT_QUEUE_SIZE);
	private BlockingQueue<File> tempFileQueue = new ArrayBlockingQueue<File>(TEMP_FILE_QUEUE_SIZE);
	
	private List<Future<Runnable>> threads = new ArrayList<Future<Runnable>>();
	
	private LifecycleState lifecycleState = LifecycleState.STOP;
		
	public RiakCSWriter(String filePrefix, String fileSuffix, String fileExtension, 
			String bucketName, String accessKey, String secretKey, boolean useHTTPS, String proxyHost, int proxyPort,
			long rollInterval, long rollSize, long rollCount) {
		this(filePrefix, fileSuffix, fileExtension, Files.createTempDir(), bucketName, accessKey, secretKey, useHTTPS, proxyHost, proxyPort,
				rollInterval, rollSize, rollCount);
	}
	
	public RiakCSWriter(String filePrefix, String fileSuffix, String fileExtension, File tempLocation, 
			String bucketName, String accessKey, String secretKey, boolean useHTTPS, String proxyHost, int proxyPort,
			long rollInterval, long rollSize, long rollCount) {
		Preconditions.checkArgument(tempLocation.exists());
		
		this.filePrefix = filePrefix;
		this.fileSuffix = fileSuffix;
		this.fileExtension = fileExtension;
		this.bucketName = bucketName;
		
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.useHTTPS = useHTTPS;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
	
		this.rollInterval = rollInterval;
		this.rollSize = rollSize;
		this.rollCount = rollCount;
	}
	
	@SuppressWarnings("unchecked")
	public void start() {
		TempFileManager tempFileManager = new TempFileManager(this.filePrefix, this.fileSuffix, this.fileExtension);
		this.threads.add((Future<Runnable>)this.executor.submit(new EventTempPersistThread(this.eventQueue, this.tempFileQueue, tempFileManager,
				this.rollInterval, this.rollSize, this.rollCount)));
		
		for (int i = 0; i < CS_UPLOAD_THREADS; ++i) {
			RiakCSFileHandler fileHandler = new RiakCSFileHandler(this.accessKey, this.secretKey, this.useHTTPS, this.proxyHost, this.proxyPort);
			this.threads.add((Future<Runnable>)this.executor.submit(new RiakCSWriterThread(this.tempFileQueue, this.bucketName, fileHandler)));
		}
		
		lifecycleState = LifecycleState.START;
	}
	

	public void stop() {
		this.executor.shutdownNow();
		try {
	        while (executor.isTerminated() == false) {
	        	this.executor.awaitTermination(CS_THREAD_WAIT, TimeUnit.MILLISECONDS);
	        }
	      } catch (InterruptedException ex) {
	        LOG.warn("shutdown interrupted on " + this.executor, ex);
	      }
		if (!this.executor.isShutdown()) {
			LOG.error("Error shutting down RiakCSWriter threadpool");
		}
		lifecycleState = LifecycleState.STOP;
	}

	public void append(Event event) {
		Preconditions.checkNotNull(event);
		
		this.eventQueue.add(event);
	}
	
	public static RiakCSWriter create(String filePrefix, String fileSuffix, String fileExtension, 
			String bucketName, String accessKey, String secretKey, boolean useHTTPS, String proxyHost, int proxyPort,
			long rollInterval, long rollSize, long rollCount) {
		return new RiakCSWriter(filePrefix, fileSuffix, fileExtension, 
			bucketName, accessKey, secretKey, useHTTPS, proxyHost, proxyPort,
			rollInterval, rollSize, rollCount);
	}
	
	private class RiakCSWriterThread implements Runnable {

		private final BlockingQueue<File> fileQueue;
		private final String bucket;
		private final RiakCSFileHandler fileHandler;
		
		public RiakCSWriterThread(BlockingQueue<File> fileQueue, String bucket, RiakCSFileHandler fileHandler) {
			this.fileQueue = fileQueue;
			this.bucket = bucket;
			this.fileHandler = fileHandler;
		}
		
		public void run() {
			try {
				while (!Thread.interrupted()) {
					File fileToUpload = this.fileQueue.poll(QUEUE_POLL_WAIT, TimeUnit.MILLISECONDS);
					
					if (fileToUpload != null) {
						boolean success = this.fileHandler.put(bucket, fileToUpload);
						if (success) {
							LOG.info("Successfully uploaded " + fileToUpload.getAbsolutePath());
							fileToUpload.delete();
						} else {
							LOG.error("Unable to upload " + fileToUpload.getAbsolutePath());
						}
					}
				}
			} catch (InterruptedException ex)  {
				LOG.error("RiakCSWriterThread interrupted, " + this.fileQueue.size() + " data files not uploaded");
			}
		}
		
	}
	
	private class EventTempPersistThread implements Runnable {

		private final BlockingQueue<Event> eventQueue;
		private final BlockingQueue<File> tempFileQueue;
		private final TempFileManager tempFileManager;
		
		private final long rollInterval;
		private final long rollCount;
		private final long rollSize;
		
		private long lastIdentifier;
		private long currentIdentifier;
		private int identifierCount = 0;
		
		private long intervalStart;
		private long byteCount;
		private long eventCount;
		
		public EventTempPersistThread(BlockingQueue<Event> eventQueue, BlockingQueue<File> tempFileQueue, 
				TempFileManager tempFileManager,
				long rollInterval, long rollSize, long rollCount) {
			
			this.eventQueue = eventQueue;
			this.tempFileQueue = tempFileQueue;
			
			this.tempFileManager = tempFileManager;
			
			this.rollInterval = rollInterval;
			this.rollSize = rollSize;
			this.rollCount = rollCount;
			
			this.intervalStart = System.currentTimeMillis();
			this.currentIdentifier = getIdentifier();
			this.lastIdentifier = currentIdentifier;
		}
		
		public void run() {
			try {
				while (!Thread.interrupted()) {
					Event event = this.eventQueue.poll(QUEUE_POLL_WAIT, TimeUnit.MILLISECONDS);
					
					if (event != null) {
						
						byte[] body = event.getBody();
						
						byteCount += body.length;
						++eventCount;
				
						String identifier = String.format("%d-%d", this.currentIdentifier, this.identifierCount);
						
						try {
							this.tempFileManager.writeData(body, identifier);
						} catch (IOException e) {
							LOG.error("Could not write event data to " + identifier, e);
						}
						
						if (shouldCreateNewFile()) {
							//debug
							
							intervalStart = System.currentTimeMillis();
							byteCount = 0;
							eventCount = 0;
							try {
								this.tempFileManager.closeIdentifier(identifier);
								LOG.info("Closed event data temp file " + identifier);
							} catch (IOException e) {
								LOG.error("Could not close event data temp file " + identifier, e);
							}
							
//							for (File file : this.tempFileManager.listAndPurgeClosedFiles()) {
//								this.tempFileQueue.put(file);
//								++tempFiles;
//							}
							this.tempFileQueue.addAll(this.tempFileManager.listAndPurgeClosedFiles());
							
							this.lastIdentifier = this.currentIdentifier;
							this.currentIdentifier = getIdentifier();
							
							if (lastIdentifier == currentIdentifier) {
								++this.identifierCount;
							} else {
								this.identifierCount = 0;
							} 

						}
					}
				}
			} catch (InterruptedException ex)  {
//				this.tempFileQueue.addAll(this.tempFileManager.listAndPurgeClosedFiles());
			} catch (RuntimeException ex) {
				System.out.println("What happened?");
			}
			
		}
		
		private long getIdentifier() {
			return this.intervalStart;
		}
		
		private long elapsedTime() {
			return System.currentTimeMillis() - this.intervalStart;
		}
		
		private boolean shouldCreateNewFile() {
			boolean newFile = false;
			
			if (elapsedTime() >= this.rollInterval) {
				newFile = true;
			}
			if (this.byteCount >= this.rollSize) {
				newFile = true;
			}
			if (this.eventCount >= this.rollCount) {
				newFile = true;
			}
			
			return newFile;
		}
		
	}

	public LifecycleState getLifecycleState() {
		return this.lifecycleState;
	}

}

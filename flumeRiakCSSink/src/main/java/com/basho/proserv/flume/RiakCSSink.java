package com.basho.proserv.flume;

import org.apache.flume.Channel;
import org.apache.flume.Clock;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class RiakCSSink extends AbstractSink implements Configurable {

	private static final Logger LOG = LoggerFactory.getLogger(RiakCSSink.class);
	private static final String NAME = "com.basho.proserv.flume.RiakCSSink";
	
	private static final String defaultAccessKey = "";
	private static final String defaultSecretKey = "";
	
	private static final boolean defaultUseHTTPS = false;
	private static final String defaultProxyHost = "";
	private static final int defaultProxyPort = 0;
	
	private static final String defaultFilePrefix = "FlumeData";
	private static final String defaultFileSuffix = "";
	private static final String defaultFileExtension = "data";
	
	private static final long defaultRollInterval = 30;
	private static final long defaultRollSize = 1024;
	private static final long defaultRollCount = 10;
	private static final long defaultBatchSize = 100;
	
	private Context context; 
	
	private String accessKey;
	private String secretKey;
	private String bucketName;
	
	private boolean useHTTPS;
	private String proxyHost;
	private int proxyPort;
	
	private String filePrefix;
	private String fileSuffix;
	private String fileExtension;
	
	private long rollInterval;
	private long rollSize;
	private long rollCount;
	private long batchSize;
	
	private Clock clock;
	private RiakCSWriter riakCSWriter;
	
	public RiakCSSink() {
	}
	
	@Override
	public void start() {
		this.riakCSWriter = RiakCSWriter.create(filePrefix, fileSuffix, fileExtension, 
			bucketName, accessKey, secretKey, useHTTPS, proxyHost, proxyPort,
			rollInterval, rollSize, rollCount);
		this.riakCSWriter.start();
		
//		super.start();
	}
	
	@Override
	public void stop() {
		this.riakCSWriter.stop();
		
//		super.stop();
	}
	
	public Status process() throws EventDeliveryException {
		Status status = Status.BACKOFF;
		
		Channel channel = getChannel();
	    Transaction transaction = channel.getTransaction();
	    transaction.begin();
	    
	    try {
	    	int txnEventCount = 0;
	        for (txnEventCount = 0; txnEventCount < batchSize; txnEventCount++) {
	          Event event = channel.take();
	          if (event == null) {
	            break;
	          }
	          this.riakCSWriter.append(event);
	        }
	    	transaction.commit();
	    	status = Status.READY;
	    } finally {
	    	transaction.close();
	    }
	    return status;
	}
	
	
	public void configure(Context context) {
		this.context = context;
		for (String key : context.getParameters().keySet()) {
			System.out.println(key);
		}
		accessKey = context.getString(NAME + ".accessKey", defaultAccessKey);
		secretKey = context.getString(NAME + ".secretKey", defaultSecretKey);
		
		useHTTPS = context.getBoolean(NAME + ".useHTTPS", defaultUseHTTPS);
		proxyHost = context.getString(NAME + ".proxyHost", defaultProxyHost);
		proxyPort = context.getInteger(NAME + ".proxyPort", defaultProxyPort);
		
		bucketName = Preconditions.checkNotNull(
		        context.getString(NAME + ".bucketName"), NAME + ".bucketName is required");

		filePrefix = context.getString(NAME + ".filePrefix", defaultFilePrefix);
		fileSuffix = context.getString(NAME + ".fileSuffix", defaultFileSuffix);
		fileExtension = context.getString(NAME + ".fileExtension", defaultFileExtension);
		
		rollInterval = context.getLong(NAME + ".rollInterval", defaultRollInterval);
		rollSize = context.getLong(NAME + ".rollSize", defaultRollSize);
		rollCount = context.getLong(NAME + ".rollCount", defaultRollCount);
		
		batchSize = context.getLong(NAME + ".batchSize", defaultBatchSize);
	}

}

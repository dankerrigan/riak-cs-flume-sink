package com.basho.proserv.flume;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.flume.Channel;
import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.Transaction;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.conf.Configurables;
import org.apache.flume.conf.FlumeConfiguration;
import org.apache.flume.lifecycle.LifecycleAware;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.flume.node.Application;
import org.apache.flume.node.MaterializedConfiguration;
import org.apache.flume.node.PollingPropertiesFileConfigurationProvider;

import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import junit.framework.TestCase;

public class RiakCSSinkTests extends TestCase {
	public void testRiakCSSink() throws Exception {
		Context context = new Context();
		context.put("riakcs.bucketName", "test_bucket");
		context.put("riakcs.batchSize", "100");
		
		RiakCSSink sink = new RiakCSSink();
		Configurables.configure(sink, context);
		
		Channel channel = new MemoryChannel();
		Configurables.configure(channel, context);
		
		sink.setChannel(channel);
		sink.start();
		
		for (Integer i = 0; i < 10; ++i) {
			Event event = new TestEvent();
			event.setBody(i.toString().getBytes());
			
			Transaction transaction = channel.getTransaction();
			transaction.begin();
			
			channel.put(event);
			
			transaction.commit();
			transaction.close();
		}
		
		sink.process();
	}
	
	public void testRiakCSSinkLive() throws Exception {
		Properties properties = new Properties();
	    properties.load(this.getClass().getResourceAsStream("riakcs.properties"));
	    
	    Context context = new Context();
	    for (Object key : properties.keySet()) {
	    	context.put((String)key, (String)properties.get(key));
	    } 
		
	    
		List<LifecycleAware> components = Lists.newArrayList();
		RiakCSSink sink = new RiakCSSink();
		Channel channel = new MemoryChannel();
	    sink.setName("RiakCSEventSink-" + UUID.randomUUID().toString());
	    
	    channel.setName("MemoryChannel-" + UUID.randomUUID().toString());
	    sink.setChannel(channel);
	    
		components.add(sink);
		components.add(channel);
		
		Configurables.configure(sink, context);
		Configurables.configure(channel, context);
		
		for (LifecycleAware component : components) {
			component.start();
		}
		
		for (Integer i = 0; i < 10; ++i) {
			Event event = new TestEvent();
			event.setBody(i.toString().getBytes());
			
			Transaction transaction = channel.getTransaction();
			transaction.begin();
			
			channel.put(event);
			
			transaction.commit();
			transaction.close();
		}
		
		sink.process();
		
		Thread.sleep(10000);
		
		for (LifecycleAware component : components) {
			component.stop();
		}
	    
	}
	
	private class TestChannel implements Channel {

		private BlockingQueue<Event> eventQueue = new ArrayBlockingQueue<Event>(10);
		
		public void start() {
			// TODO Auto-generated method stub
			
		}

		public void stop() {
			// TODO Auto-generated method stub
			
		}

		public LifecycleState getLifecycleState() {
			// TODO Auto-generated method stub
			return null;
		}

		public void setName(String name) {
			// TODO Auto-generated method stub
			
		}

		public String getName() {
			// TODO Auto-generated method stub
			return null;
		}

		public void put(Event event) throws ChannelException {
			try {
				this.eventQueue.put(event);
			} catch (InterruptedException e) {
				throw new ChannelException(e);
			}
			
		}

		public Event take() throws ChannelException {
			try {
				return this.eventQueue.poll(100l, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				throw new ChannelException(e);
			}
		}

		public Transaction getTransaction() {
			return new TestTransaction();
		}
		
		private class TestTransaction implements Transaction {

			public void begin() {
				// TODO Auto-generated method stub
				
			}

			public void commit() {
				// TODO Auto-generated method stub
				
			}

			public void rollback() {
				// TODO Auto-generated method stub
				
			}

			public void close() {
				// TODO Auto-generated method stub
				
			}
			
		}
		
	}
	
	private class TestEvent implements Event {
		
		private Map<String, String> headers = new HashMap<String, String>();
		byte[] body = new byte[0];
		
		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public byte[] getBody() {
			return body;
		}

		public void setBody(byte[] body) {
			this.body = body;
		}
		
	}
}

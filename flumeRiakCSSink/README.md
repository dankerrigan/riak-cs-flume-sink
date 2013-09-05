#Riak CS Sink for Apache Flume

##Why?
Apache Flume works with the Hadoop project which already has a HDFS to S3 connector.  Since Riak CS doesn't currently have support for the PUT Object (Copy) operation in the S3 API (scheduled for CS 1.5), this CS specific Sink was written.  Additionally, the Sink does simple rolling file uploads into Riak CS, bypassing the step of uploading to an temporary location first.  This cuts down on some overhead.  Finally, this Sink should work with S3 as well since it uses the S3 API.

##Build instructions
	git clone http://github.com/dankerrigan/flume-riak-cs-sink
	cd flume-riak-cs-sink
	mvn clean package

A target directory will be created which contains a .tar.gz file.

## Installation and Configuration
	mkdir ${FLUME_HOME}/plugins.d
	cp ${FLUME_RIAK_CS_SINK}/target/*.tar.gz ${FLUME_HOME}/plugins.d/
	cd ${FLUME_HOME}/plugins.d
	tar -xzvf *.tar.gz

Add the following content to your ${FLUME_HOME}/conf/flume-site.xml

	<configuration>
  		<property>
    	<name>flume.plugin.classes</name>
    	<value>com.basho.proserv.flume.RiakCSSink</value>
    	<description>Sink that interfaces directly to S3, bypassing the HDFS plugin</description>
  		</property>
	</configuration>

Add the Riak CS Flume sink in your Flume configuration file.  Below is a sample configuration file that uses a netcat source and the Riak CS Sink

	agent.sources = netcat
	agent.channels = memoryChannel
	agent.sinks = RiakCS
	
	# For each one of the sources, the type is defined
	agent.sources.netcat.type = netcat
	agent.sources.netcat.bind = localhost
	agent.sources.netcat.port = 44444
	
	# The channel can be defined as follows.
	agent.sources.netcat.channels = memoryChannel
	
	# Each sink's type must be defined
	agent.sinks.RiakCS.type = com.basho.proserv.flume.RiakCSSink
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.accessKey =  <YourS3AccessKeyHere>
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.secretKey =  <YourS3SecretKeyHere>
	# Bucket must exist
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.bucketName = 	test
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.https = false
	# Comment out next 2 lines if not using a Proxy
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.proxyHost = 	localhost
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.proxyPort = 	8080
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.filePrefix = 	FlumeData
	#agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.fileSuffix = 
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.fileExtension 	= data
	# Interval in milliseconds
	#agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.rollInterval 	=
	# Size in bytes
	#agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.rollSize =
	# Number of events
	agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.rollCount = 1000
	#agent.sinks.RiakCS.com.basho.proserv.flume.RiakCSSink.batchSize = 1000
	
	
	#Specify the channel the sink should use
	agent.sinks.RiakCS.channel = memoryChannel
	
	# Each channel's type is defined.
	agent.channels.memoryChannel.type = memory
	
	# Other config values specific to each type of channel(sink or 	source)
	# can be defined as well
	# In this case, it specifies the capacity of the memory channel
	agent.channels.memoryChannel.capacity = 100

## Running Flume
To run Flume in a simple agent mode, referring to the config file above, use the following command:

	cd ${FLUME_HOME}
	./bin/flume-ng agent --conf agent --conf-file ./conf/flume-conf.properties --name agent -Dflume.root.logger=INFO,console --plugins-path ./plugins.d

## Remaining Work
* Ensure correct error handling according to Flume standards
* Handle Riak CS failure conditions gracefully
* Handle shutdown condition correctly
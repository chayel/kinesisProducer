/*
 * Copyright 2013-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package samples.json;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import samples.json.KinesisUtils;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.kinesis.connectors.KinesisConnectorConfiguration;
import com.amazonaws.services.kinesis.connectors.KinesisConnectorExecutorBase;
import com.amazonaws.services.redshift.AmazonRedshiftClient;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * This class defines the execution of a Amazon Kinesis Connector.
 * 
 */
public abstract class KinesisConnectorExecutor<T, U> extends KinesisConnectorExecutorBase<T, U> {

    private static final Log LOG = LogFactory.getLog(KinesisConnectorExecutor.class);

    // Create AWS Resource constants
    private static final String CREATE_KINESIS_INPUT_STREAM = "createKinesisInputStream";
    private static final String CREATE_KINESIS_OUTPUT_STREAM = "createKinesisOutputStream";
    private static final boolean DEFAULT_CREATE_RESOURCES = false;

    // Create Stream Source constants
    private static final String CREATE_STREAM_SOURCE = "createStreamSource";
    private static final String LOOP_OVER_STREAM_SOURCE = "loopOverStreamSource";
    private static final boolean DEFAULT_CREATE_STREAM_SOURCE = false;
    private static final boolean DEFAULT_LOOP_OVER_STREAM_SOURCE = false;
    private static final String INPUT_STREAM_FILE = "inputStreamFile";

    // Class variables
    protected final KinesisConnectorConfiguration config;
    private final Properties properties;

    /**
     * Create a new KinesisConnectorExecutor based on the provided configuration (*.propertes) file.
     * 
     * @param configFile
     *        The name of the configuration file to look for on the classpath
     */
    public KinesisConnectorExecutor(String configFile) {
        InputStream configStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(configFile);

        if (configStream == null) {
            String msg = "Could not find resource " + configFile + " in the classpath";
            throw new IllegalStateException(msg);
        }
        properties = new Properties();
        try {
            properties.load(configStream);
            configStream.close();
        } catch (IOException e) {
            String msg = "Could not load properties file " + configFile + " from classpath";
            throw new IllegalStateException(msg, e);
        }
        this.config = new KinesisConnectorConfiguration(properties, getAWSCredentialsProvider());
        setupAWSResources();
        setupInputStream();

        // Initialize executor with configurations
        super.initialize(config);
    }

    /**
     * Returns an {@link AWSCredentialsProvider} with the permissions necessary to accomplish all specified
     * tasks. At the minimum it will require read permissions for Amazon Kinesis. Additional read permissions
     * and write permissions may be required based on the Pipeline used.
     * 
     * @return
     */
    public AWSCredentialsProvider getAWSCredentialsProvider() {
        return new DefaultAWSCredentialsProviderChain();
    }

    /**
     * Setup necessary AWS resources for the samples. By default, the Executor does not create any
     * AWS resources. The user must specify true for the specific create properties in the
     * configuration file.
     */
    private void setupAWSResources() {
        if (parseBoolean(CREATE_KINESIS_INPUT_STREAM, DEFAULT_CREATE_RESOURCES, properties)) {
            KinesisUtils.createInputStream(config);
        }

        if (parseBoolean(CREATE_KINESIS_OUTPUT_STREAM, DEFAULT_CREATE_RESOURCES, properties)) {
            KinesisUtils.createOutputStream(config);
        }

    }

    /**
     * Helper method to spawn the {@link StreamSource} in a separate thread.
     */
    private void setupInputStream() {
        if (parseBoolean(CREATE_STREAM_SOURCE, DEFAULT_CREATE_STREAM_SOURCE, properties)) {
            String inputFile = properties.getProperty(INPUT_STREAM_FILE);
            StreamSource streamSource;
            if (config.BATCH_RECORDS_IN_PUT_REQUEST) {
                streamSource =
                        new BatchedStreamSource(config, inputFile, parseBoolean(LOOP_OVER_STREAM_SOURCE,
                                DEFAULT_LOOP_OVER_STREAM_SOURCE,
                                properties));

            } else {
                streamSource =
                        new StreamSource(config, inputFile, parseBoolean(LOOP_OVER_STREAM_SOURCE,
                                DEFAULT_LOOP_OVER_STREAM_SOURCE,
                                properties));
            }
            Thread streamSourceThread = new Thread(streamSource);
            LOG.info("Starting stream source.");
            streamSourceThread.start();
        }
    }


    /**
     * Helper method used to parse boolean properties.
     * 
     * @param property
     *        The String key for the property
     * @param defaultValue
     *        The default value for the boolean property
     * @param properties
     *        The properties file to get property from
     * @return property from property file, or if it is not specified, the default value
     */
    private static boolean parseBoolean(String property, boolean defaultValue, Properties properties) {
        return Boolean.parseBoolean(properties.getProperty(property, Boolean.toString(defaultValue)));
    }

    /**
     * Helper method used to parse long properties.
     * 
     * @param property
     *        The String key for the property
     * @param defaultValue
     *        The default value for the long property
     * @param properties
     *        The properties file to get property from
     * @return property from property file, or if it is not specified, the default value
     */
    private static long parseLong(String property, long defaultValue, Properties properties) {
        return Long.parseLong(properties.getProperty(property, Long.toString(defaultValue)));
    }

    /**
     * Helper method used to parse integer properties.
     * 
     * @param property
     *        The String key for the property
     * @param defaultValue
     *        The default value for the integer property
     * @param properties
     *        The properties file to get property from
     * @return property from property file, or if it is not specified, the default value
     */
    private static int parseInt(String property, int defaultValue, Properties properties) {
        return Integer.parseInt(properties.getProperty(property, Integer.toString(defaultValue)));
    }
}

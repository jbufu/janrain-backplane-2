/*
 * Copyright 2012 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane2.server.config;

import com.amazonaws.auth.AWSCredentials;
import org.apache.log4j.Logger;

/**
 * Extracts and provides Amazon Web Service credentials (key id and key secret)
 * from the AWS_ACCESS_KEY_ID and AWS_SECRET_KEY system properties.
 *
 * @author Johnny Bufu
 */
public class SystemPropsAWSCredentials implements AWSCredentials {

    // - PUBLIC

    @Override
    public String getAWSAccessKeyId() {
        return awsKeyId;
    }

    @Override
    public String getAWSSecretKey() {
        return awsSecretKey;
    }

    // - PRIVATE

    private SystemPropsAWSCredentials() {
        this.awsKeyId = Backplane2Config.getAwsProp(AWS_ACCESS_KEY_ID);
        this.awsSecretKey = Backplane2Config.getAwsProp(AWS_SECRET_KEY);
        logger.info("AWS credentials loaded from system properties for Key ID: " + awsKeyId);
    }

    private final String awsKeyId;
    private final String awsSecretKey;

    private static final Logger logger = Logger.getLogger(SystemPropsAWSCredentials.class);

    private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    private static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
}

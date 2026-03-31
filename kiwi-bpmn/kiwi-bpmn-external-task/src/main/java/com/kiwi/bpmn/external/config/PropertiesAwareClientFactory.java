/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kiwi.bpmn.external.config;

import com.kiwi.bpmn.external.LocalExternalTaskClientBuild;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.ExternalTaskClientBuilder;
import org.camunda.bpm.client.spring.impl.client.ClientConfiguration;
import org.camunda.bpm.client.spring.impl.client.ClientFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;

public class PropertiesAwareClientFactory extends ClientFactory {

  @Autowired
  protected ClientProperties clientProperties;

  @Autowired
  protected ProcessEngine processEngine;

  @Override
  public void afterPropertiesSet() throws Exception {
    applyPropertiesFrom(clientProperties);
    super.afterPropertiesSet();
  }

  public void applyPropertiesFrom(ClientProperties clientConfigurationProps) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    if (clientConfigurationProps.getBaseUrl() != null) {
      clientConfiguration.setBaseUrl(clientConfigurationProps.getBaseUrl());
    }
    if (clientConfigurationProps.getWorkerId() != null) {
      clientConfiguration.setWorkerId(clientConfigurationProps.getWorkerId());
    }
    if (clientConfigurationProps.getMaxTasks() != null) {
      clientConfiguration.setMaxTasks(clientConfigurationProps.getMaxTasks());
    }
    if (clientConfigurationProps.getUsePriority() != null && !clientConfigurationProps.getUsePriority()) {
      clientConfiguration.setUsePriority(false);
    }
    if (clientConfigurationProps.getDefaultSerializationFormat() != null) {
      clientConfiguration.setDefaultSerializationFormat(clientConfigurationProps.getDefaultSerializationFormat());
    }
    if (clientConfigurationProps.getDateFormat() != null) {
      clientConfiguration.setDateFormat(clientConfigurationProps.getDateFormat());
    }
    if (clientConfigurationProps.getLockDuration() != null) {
      clientConfiguration.setLockDuration(clientConfigurationProps.getLockDuration());
    }
    if (clientConfigurationProps.getAsyncResponseTimeout() != null) {
      clientConfiguration.setAsyncResponseTimeout(clientConfigurationProps.getAsyncResponseTimeout());
    }
    if (clientConfigurationProps.getDisableAutoFetching() != null &&
        clientConfigurationProps.getDisableAutoFetching()) {
      clientConfiguration.setDisableAutoFetching(true);
    }
    if (clientConfigurationProps.getDisableBackoffStrategy() != null &&
        clientConfigurationProps.getDisableBackoffStrategy()) {
      clientConfiguration.setDisableBackoffStrategy(true);
    }
    setClientConfiguration(clientConfiguration);
  }


    @Override
    public ExternalTaskClient getObject() {
        if (client == null) {
            ExternalTaskClientBuilder clientBuilder = new LocalExternalTaskClientBuild(processEngine);
//            if (clientConfiguration.getBaseUrl() != null) {
//                clientBuilder.baseUrl(resolve(clientConfiguration.getBaseUrl()));
//            }
            clientBuilder.baseUrl("http://localhost:8088/engine-rest");
            if (clientConfiguration.getWorkerId() != null) {
                clientBuilder.workerId(resolve(clientConfiguration.getWorkerId()));
            }

//            addClientRequestInterceptors(clientBuilder);

            if (clientConfiguration.getMaxTasks() != null) {
                clientBuilder.maxTasks(clientConfiguration.getMaxTasks());
            }
            if (clientConfiguration.getUsePriority() != null && !clientConfiguration.getUsePriority()) {
                clientBuilder.usePriority(false);
            }
            if (clientConfiguration.getDefaultSerializationFormat() != null) {
                clientBuilder.defaultSerializationFormat(resolve(clientConfiguration.getDefaultSerializationFormat()));
            }
            if (clientConfiguration.getDateFormat() != null) {
                clientBuilder.dateFormat(resolve(clientConfiguration.getDateFormat()));
            }
            if (clientConfiguration.getAsyncResponseTimeout() != null) {
                clientBuilder.asyncResponseTimeout(clientConfiguration.getAsyncResponseTimeout());
            }
            if (clientConfiguration.getLockDuration() != null) {
                clientBuilder.lockDuration(clientConfiguration.getLockDuration());
            }
            if (clientConfiguration.getDisableAutoFetching() != null &&
                    clientConfiguration.getDisableAutoFetching()) {
                clientBuilder.disableAutoFetching();
            }
            if (clientConfiguration.getDisableBackoffStrategy() != null &&
                    clientConfiguration.getDisableBackoffStrategy()) {
                clientBuilder.disableBackoffStrategy();
            }
            if (backoffStrategy != null) {
                clientBuilder.backoffStrategy(backoffStrategy);
            }
            client = clientBuilder.build();
        }

        LOG.bootstrapped();

        return client;
    }

}
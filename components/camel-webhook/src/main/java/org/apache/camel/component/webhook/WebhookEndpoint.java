/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.webhook;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * A meta-endpoint that pushes webhook data into a delegate {@code WebhookCapableEndpoint}.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "webhook", title = "Webhook", syntax = "webhook:endpointUri", label = "cloud", lenientProperties = true)
public class WebhookEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private WebhookCapableEndpoint delegateEndpoint;

    @UriParam(label = "advanced")
    private WebhookConfiguration configuration;

    public WebhookEndpoint(String uri, WebhookComponent component, WebhookConfiguration configuration, String delegateUri) {
        super(uri, component);
        this.configuration = configuration;

        Endpoint delegate = getCamelContext().getEndpoint(delegateUri);
        if (!(delegate instanceof WebhookCapableEndpoint)) {
            throw new IllegalArgumentException("The provided endpoint is not capable of being used in webhook mode: " + delegateUri);
        }
        delegateEndpoint = (WebhookCapableEndpoint) delegate;
        delegateEndpoint.setWebhookConfiguration(configuration);
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException("You cannot create a producer with the webhook endpoint.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RestConsumerFactory factory = WebhookUtils.locateRestConsumerFactory(getCamelContext(), configuration);

        String path = configuration.computeFullPath(false);
        String serverUrl = configuration.computeServerUriPrefix();
        String url = serverUrl + path;

        Processor handler = delegateEndpoint.createWebhookHandler(processor);

        return new MultiRestConsumer(getCamelContext(), factory, this, handler, delegateEndpoint.getWebhookMethods(), url, path,
                configuration.getRestConfiguration(), this::configureConsumer);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.isWebhookAutoRegister()) {
            log.info("Registering webhook for endpoint " + delegateEndpoint);
            delegateEndpoint.registerWebhook();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (configuration.isWebhookAutoRegister()) {
            log.info("Unregistering webhook for endpoint " + delegateEndpoint);
            delegateEndpoint.unregisterWebhook();
        }
    }

    @Override
    public WebhookCapableEndpoint getEndpoint() {
        return delegateEndpoint;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
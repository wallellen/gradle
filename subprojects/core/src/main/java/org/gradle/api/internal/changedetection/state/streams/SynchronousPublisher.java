/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state.streams;

import java.util.LinkedList;
import java.util.List;

public abstract class SynchronousPublisher<T> extends AbstractPublisher<T> {
    private List<Subscriber<? super T>> subscribers = new LinkedList<Subscriber<? super T>>();

    public List<Subscriber<? super T>> getSubscribers() {
        return subscribers;
    }

    @Override
    public <V extends Subscriber<? super T>> V subscribe(V subscriber) {
        this.subscribers.add(subscriber);
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request() {
                doRequest();
            }
        });
        return subscriber;
    }

    protected abstract void doRequest();
}

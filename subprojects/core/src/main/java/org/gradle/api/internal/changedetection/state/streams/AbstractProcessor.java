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

import org.gradle.internal.UncheckedException;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractProcessor<R, T> implements Processor<R, T> {
    private List<Subscriber<? super T>> subscribers = new LinkedList<Subscriber<? super T>>();
    private BlockingQueue<Subscription> parentSubscriptions = new LinkedBlockingQueue<Subscription>();

    public List<Subscriber<? super T>> getSubscribers() {
        return subscribers;
    }

    @Override
    public <V extends Subscriber<? super T>> V subscribe(V subscriber) {
        this.subscribers.add(subscriber);
        if (!parentSubscriptions.isEmpty()) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request() {
                    AbstractProcessor.this.request();
                    while (!parentSubscriptions.isEmpty()) {
                        try {
                            parentSubscriptions.take().request();
                        } catch (InterruptedException e) {
                            throw new UncheckedException(e);
                        }
                    }
                }
            });
        }
        return subscriber;
    }

    protected void request() {}

    @Override
    public void onError(Exception error) {
        for (Subscriber<? super T> subscriber : subscribers) {
            subscriber.onError(error);
        }
    }

    @Override
    public void onCompleted() {
        for (Subscriber<? super T> subscriber : subscribers) {
            subscriber.onCompleted();
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        parentSubscriptions.offer(subscription);
        for (Subscriber<? super T> subscriber : subscribers) {
            subscriber.onSubscribe(subscription);
        }
    }
}

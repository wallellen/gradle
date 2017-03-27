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

import org.gradle.api.specs.Spec;

import java.util.concurrent.atomic.AtomicInteger;

public class Publishers {
    public static <T> SynchronousPublisher<T> create(final Iterable<? extends T> inputs) {
        return new SynchronousPublisher<T>() {
            boolean published;

            @Override
            public void doRequest() {
                if (!published) {
                    published = true;
                    for (T input : inputs) {
                        for (Subscriber<? super T> subscriber : getSubscribers()) {
                            subscriber.onNext(input);
                        }
                    }
                    for (Subscriber<? super T> subscriber : getSubscribers()) {
                        subscriber.onCompleted();
                    }
                }
            }
        };
    }

    public static <T> Publisher<T> filter(Publisher<T> publisher, final Spec<T> spec) {
        Processor<T, T> processor = new AbstractProcessor<T, T>() {
            @Override
            public void onNext(T next) {
                if (spec.isSatisfiedBy(next)) {
                    for (Subscriber<? super T> subscriber : getSubscribers()) {
                        subscriber.onNext(next);
                    }
                }
            }
        };
        publisher.subscribe(processor);
        return processor;
    }

    public static <T> Publisher<T> split(Publisher<T> publisher, final Spec<T> spec) {
        Processor<T, T> processor = new AbstractProcessor<T, T>() {
            @Override
            public void onNext(T next) {
                if (spec.isSatisfiedBy(next)) {
                    for (Subscriber<? super T> subscriber : getSubscribers()) {
                        subscriber.onNext(next);
                    }
                }
            }
        };
        publisher.subscribe(processor);
        return processor;
    }

    public static <T> Publisher<T> flatten(Publisher<Publisher<T>> toFlatten) {
        AbstractProcessor<Publisher<T>, T> processor = new FlatteningProcessor<T>();
        toFlatten.subscribe(processor);
        return processor;
    }

    public static <T> Publisher<T> join(Publisher<T> left, Publisher<T> right) {
        AbstractProcessor<T, T> processor = new AbstractProcessor<T, T>() {
            private final AtomicInteger completed = new AtomicInteger(2);

            @Override
            public void onNext(T next) {
                for (Subscriber<? super T> subscriber : getSubscribers()) {
                    subscriber.onNext(next);
                }
            }

            @Override
            public void onCompleted() {
                if (completed.decrementAndGet() == 0) {
                    super.onCompleted();
                }
            }
        };
        left.subscribe(processor);
        right.subscribe(processor);
        return processor;
    }

    private static class FlatteningProcessor<T> extends AbstractProcessor<Publisher<T>, T> {

        private Subscriber<T> delegatingSubscriber = new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request();
            }

            @Override
            public void onNext(T next) {
                for (Subscriber<? super T> subscriber : getSubscribers()) {
                    subscriber.onNext(next);
                }
            }

            @Override
            public void onError(Exception error) {
                FlatteningProcessor.this.onError(error);
            }

            @Override
            public void onCompleted() {
            }
        };

        @Override
        public void onNext(Publisher<T> next) {
            next.subscribe(delegatingSubscriber);
        }
    }
}

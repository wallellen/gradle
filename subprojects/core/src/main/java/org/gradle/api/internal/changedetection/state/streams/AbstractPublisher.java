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

import com.google.common.base.Function;
import org.gradle.api.specs.Spec;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractPublisher<T> implements Publisher<T> {
    public <V> Publisher<V> map(final Function<? super T, ? extends V> function) {
        return subscribe(new AbstractProcessor<T, V>() {
            @Override
            public void onNext(T next) {
                for (Subscriber<? super V> subscriber : getSubscribers()) {
                    subscriber.onNext(function.apply(next));
                }
            }
        });
    }

    public Publisher<T> filter(final Spec<? super T> spec) {
        return subscribe(new FilteringProcessor<T>(spec));
    }
    public Publisher<T> join(Publisher<T> right) {
        AbstractProcessor<T, T> processor = new JoiningProcessor<T>();
        subscribe(processor);
        right.subscribe(processor);
        return processor;
    }

    @Override
    public <V> Publisher<V> flatMap(Function<T, Publisher<V>> function) {
        AbstractProcessor<T, V> processor = new FlatteningProcessor<T, V>(function);
        subscribe(processor);
        return processor;
    }

    private static class FlatteningProcessor<T, V> extends AbstractProcessor<T, V> {

        private final Function<T, Publisher<V>> function;
        private Subscriber<V> delegatingSubscriber = new Subscriber<V>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request();
            }

            @Override
            public void onNext(V next) {
                for (Subscriber<? super V> subscriber : getSubscribers()) {
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

        FlatteningProcessor(Function<T, Publisher<V>> function) {
            this.function = function;
        }

        @Override
        public void onNext(T next) {
            function.apply(next).subscribe(delegatingSubscriber);
        }
    }

    private static class JoiningProcessor<T> extends AbstractProcessor<T, T> {
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
    }
}

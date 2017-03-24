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

package org.gradle.api.internal.changedetection.state.observers;

import com.google.common.base.Function;
import org.gradle.api.specs.Spec;

public class Publishers {
    public static <T, V> Publisher<V> map(final Publisher<T> publisher, final Function<T, V> function) {
        Processor<T, V> processor = new AbstractProcessor<T, V>() {
            @Override
            public void onNext(T next) {
                getSubscriber().onNext(function.apply(next));
            }

            @Override
            public void onSubscribe() {
                getSubscriber().onSubscribe();
            }
        };
        publisher.subscribe(processor);
        return processor;
    }

    public static <T> SynchronousPublisher<T> create(final Iterable<? extends T> inputs) {
        return new SynchronousPublisher<T>() {
            @Override
            public void publish() {
                getSubscriber().onSubscribe();
                for (T input : inputs) {
                    getSubscriber().onNext(input);
                }
                getSubscriber().onCompleted();
            }
        };
    }

    public static <T> Publisher<T> processMatching(Publisher<T> publisher, Spec<T> spec, Function<Publisher<T>, >)
}

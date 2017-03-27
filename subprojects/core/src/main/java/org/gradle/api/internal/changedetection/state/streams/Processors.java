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

public class Processors {
    public static <R, T> Processor<R, T> compose(final Subscriber<R> initialSubscriber, final Publisher<T> finalPublisher) {
        return new ComposedProcessor<R, T>(initialSubscriber, finalPublisher);
    }

    public static <T> Processor<T, T> identity() {
        return new AbstractProcessor<T, T>() {
            @Override
            public void onNext(T next) {
                allSubscribersOnNext(next);
            }
        };
    }

    private static class ComposedProcessor<R, T> extends AbstractPublisher<T> implements Processor<R, T> {
        private final Subscriber<R> initialSubscriber;
        private final Publisher<T> finalPublisher;

        public ComposedProcessor(Subscriber<R> initialSubscriber, Publisher<T> finalPublisher) {
            this.initialSubscriber = initialSubscriber;
            this.finalPublisher = finalPublisher;
        }

        @Override
        public <V extends Subscriber<? super T>> V subscribe(V subscriber) {
            return finalPublisher.subscribe(subscriber);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            initialSubscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(R next) {
            initialSubscriber.onNext(next);
        }

        @Override
        public void onError(Exception error) {
            initialSubscriber.onError(error);
        }

        @Override
        public void onCompleted() {
            initialSubscriber.onCompleted();
        }
    }
}

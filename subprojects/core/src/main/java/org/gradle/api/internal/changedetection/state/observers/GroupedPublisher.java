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

public class GroupedPublisher<K, V> implements Publisher<V> {
    private final K key;
    private final Publisher<V> publisher;

    public static <S, T> GroupedPublisher<S, T> from(final S key, final Publisher<T> publisher) {
        return new GroupedPublisher<S, T>(key, publisher);
    }

    public GroupedPublisher(K key, Publisher<V> publisher) {
        this.key = key;
        this.publisher = publisher;
    }

    public K getKey() {
        return key;
    }

    @Override
    public void subscribe(Subscriber<? super V> subscriber) {
        publisher.subscribe(subscriber);
    }
}

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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class CollectingSubscriber<T> implements Subscriber<T> {
    private List<T> collector;

    public List<T> getCollection() {
        return collector;
    }

    private Exception error;

    @Override
    public void onSubscribe() {
        collector = new ArrayList<T>();
        error = null;
    }

    @Override
    public void onNext(T next) {
        Preconditions.checkState(!isError(), "No processing when error");
        collector.add(next);
    }

    @Override
    public void onError(Exception error) {
        Preconditions.checkState(!isError(), "No processing when error");
        this.error = error;
    }

    @Override
    public void onCompleted() {
        Preconditions.checkState(!isError(), "No processing when error");
        collector = ImmutableList.copyOf(collector);
    }

    public boolean isError() {
        return error != null;
    }
}

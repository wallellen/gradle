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

class FilteringProcessor<T> extends AbstractProcessor<T, T> {
    private final Spec<? super T> spec;

    public FilteringProcessor(Spec<? super T> spec) {
        this.spec = spec;
    }

    @Override
    public void onNext(T next) {
        if (spec.isSatisfiedBy(next)) {
            for (Subscriber<? super T> subscriber : getSubscribers()) {
                subscriber.onNext(next);
            }
        }
    }
}

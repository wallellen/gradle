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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.state.streams.AbstractProcessor;
import org.gradle.api.internal.changedetection.state.streams.Processor;
import org.gradle.api.internal.changedetection.state.streams.Subscriber;
import org.gradle.cache.PersistentIndexedCache;

public class CachingProcessor<S extends FileDetails> extends AbstractProcessor<S, FileDetails> {
    private final PersistentIndexedCache<HashCode, HashCode> persistentCache;
    private final Processor<S, FileDetails> processor;

    public CachingProcessor(PersistentIndexedCache<HashCode, HashCode> persistentCache, Processor<S, FileDetails> processor) {
        this.persistentCache = persistentCache;
        this.processor = processor;
    }

    @Override
    public <V extends Subscriber<? super FileDetails>> V subscribe(V subscriber) {
        processor.subscribe(subscriber);
        return super.subscribe(subscriber);
    }

    @Override
    public void onNext(S next) {
        HashCode loaded = persistentCache.get(next.getContent().getContentMd5());
        if (loaded != null) {
            for (Subscriber<? super FileDetails> subscriber : getSubscribers()) {
                subscriber.onNext(DefaultFileDetails.copyOf(next).withContentHash(loaded));
            }
        } else {
            processor.onNext(next);
        }
    }
}

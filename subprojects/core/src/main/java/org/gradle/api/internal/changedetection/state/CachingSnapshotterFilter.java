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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.cache.PersistentIndexedCache;

import java.util.List;

public class CachingSnapshotterFilter implements SnapshotterFilter {
    private final SnapshotterFilter delegate;
    private final PersistentIndexedCache<HashCode, List<FileDetails>> persistentCache;

    public CachingSnapshotterFilter(SnapshotterFilter delegate, PersistentIndexedCache<HashCode, List<FileDetails>> persistentCache) {
        this.delegate = delegate;
        this.persistentCache = persistentCache;
    }

    @Override
    public Iterable<FileDetails> filter(Iterable<SnapshottableFileDetails> details) {
        HashCode key = hash(details);
        Iterable<FileDetails> filtered = persistentCache.get(key);
        if (filtered == null) {
            filtered = delegate.filter(details);
            persistentCache.put(key, ImmutableList.copyOf(filtered));
        }
        return filtered;
    }

    private HashCode hash(Iterable<SnapshottableFileDetails> details) {
        Hasher hasher = Hashing.md5().newHasher();
        for (SnapshottableFileDetails detail : details) {
            hasher.putBytes(detail.getContent().getContentMd5().asBytes());
        }
        return hasher.hash();
    }
}

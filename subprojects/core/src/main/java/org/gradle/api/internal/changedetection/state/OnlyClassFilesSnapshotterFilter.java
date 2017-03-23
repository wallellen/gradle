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
import org.gradle.internal.FileUtils;

public class OnlyClassFilesSnapshotterFilter implements SnapshotterFilter {
    private final SnapshotterFilter delegate;

    public OnlyClassFilesSnapshotterFilter(SnapshotterFilter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Iterable<FileDetails> filter(Iterable<SnapshottableFileDetails> details) {
        ImmutableList.Builder<SnapshottableFileDetails> filtered = ImmutableList.builder();
        for (SnapshottableFileDetails detail : details) {
            if (FileUtils.isClass(detail.getName())) {
                filtered.add(detail);
            }
        }
        return delegate.filter(filtered.build());
    }
}

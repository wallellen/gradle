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

import com.google.common.base.Function;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.observers.Publisher;
import org.gradle.api.internal.changedetection.state.observers.Publishers;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

public class PublishingClasspathSnaphotter extends PublishingFileCollectionSnapshotter implements ClasspathSnapshotter {
    public PublishingClasspathSnaphotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror, Function<Publisher<FileDetails>, Publisher<FileDetails>> transformer) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new Function<Publisher<FileDetails>, Publisher<FileDetails>>() {
                @Override
                public Publisher<FileDetails> apply(Publisher<FileDetails> publisher) {
                    Publishers.map(publisher, new PhysicalSnapshot());
                    return null;
                }
            }
        );
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return ClasspathSnapshotter.class;
    }

    private static class PhysicalSnapshot implements Function<FileDetails, SnapshottableFileDetails> {
        @Override
        public SnapshottableFileDetails apply(FileDetails input) {
            return new DefaultPhysicalFileDetails(input);
        }
    }
}

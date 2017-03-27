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
import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.streams.GroupedPublisher;
import org.gradle.api.internal.changedetection.state.streams.Processor;
import org.gradle.api.internal.changedetection.state.streams.Processors;
import org.gradle.api.internal.changedetection.state.streams.Publisher;
import org.gradle.api.internal.changedetection.state.streams.SortingProcessor;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.Comparator;

import static org.gradle.api.internal.changedetection.state.streams.Processors.compose;

public class PublishingClasspathSnaphotter extends PublishingFileCollectionSnapshotter implements ClasspathSnapshotter {
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR = new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };
    public static final Spec<FileDetails> IS_ROOT_JAR_FILE = new Spec<FileDetails>() {
        @Override
        public boolean isSatisfiedBy(FileDetails element) {
            return FileUtils.isJar(element.getName()) && element.isRoot() && element.getType() == FileType.RegularFile;
        }
    };

    public PublishingClasspathSnaphotter(final FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror, PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new ClasspathSnapshotterProcessorFactory(persistentCache, hasher)
        );
    }


    public static class ClasspathSnapshotterProcessorFactory implements Factory<Processor<FileDetails, FileDetails>> {
        private final PersistentIndexedCache<HashCode, HashCode> persistentCache;
        private final FileHasher hasher;

        public ClasspathSnapshotterProcessorFactory(PersistentIndexedCache<HashCode, HashCode> persistentCache, FileHasher hasher) {
            this.persistentCache = persistentCache;
            this.hasher = hasher;
        }

        @Override
        public Processor<FileDetails, FileDetails> create() {
            Processor<FileDetails, FileDetails> start = Processors.identity();
            Publisher<FileDetails> regularFiles = start.filter(Specs.negate(IS_ROOT_JAR_FILE));
            Publisher<FileDetails> rootJarFiles = start.filter(IS_ROOT_JAR_FILE);

            Processor<FileDetails, FileDetails> jarContentsHashed =
                rootJarFiles.subscribe(cache(hashZipContents(hasher)));
            return compose(start, regularFiles.join(jarContentsHashed).subscribe(sort()));
        }

        private <S extends FileDetails> Processor<S, FileDetails> cache(Processor<S, FileDetails> processor) {
            return new CachingProcessor<S>(persistentCache, processor);
        }
    }

    public static Processor<FileDetails, FileDetails> hashZipContents(FileHasher hasher) {
        Processor<FileDetails, FileDetails> start = Processors.identity();
        Publisher<FileDetails> end = expandZip(hasher, start).flatMap(new CombineZipHash());
        return compose(start, end);
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return ClasspathSnapshotter.class;
    }

    public static class PhysicalSnapshot implements Function<FileDetails, SnapshottableFileDetails> {
        @Override
        public SnapshottableFileDetails apply(FileDetails input) {
            return new DefaultPhysicalFileDetails(input);
        }
    }

    public static Publisher<GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails>> expandZip(FileHasher hasher, Publisher<FileDetails> publisher) {
        return publisher.map(new PhysicalSnapshot()).subscribe(new ExpandZipProcessor(hasher));
    }

    public static class CombineZipHash implements Function<GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails>, Publisher<FileDetails>> {
        @Override
        public Publisher<FileDetails> apply(GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails> input) {
            return input.map(cleanup()).subscribe(sort()).subscribe(new CombineHashes(input.getKey()));
        }
    }

    public static PublishingFileCollectionSnapshotter.CleanupFileDetails cleanup() {
        return new CleanupFileDetails();
    }
    public static SortingProcessor<FileDetails> sort() {
        return new SortingProcessor<FileDetails>(FILE_DETAILS_COMPARATOR);
    }
}

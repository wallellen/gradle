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
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.Collections;

import static org.gradle.api.internal.changedetection.state.PublishingClasspathSnaphotter.*;
import static org.gradle.api.internal.changedetection.state.streams.Processors.compose;

public class PublishingCompileClasspathSnapshotter extends PublishingFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    public static final Spec<FileDetails> IS_CLASS_FILE = new Spec<FileDetails>() {
        @Override
        public boolean isSatisfiedBy(FileDetails element) {
            return FileUtils.isClass(element.getName()) &&  element.getType() == FileType.RegularFile;
        }
    };

    public PublishingCompileClasspathSnapshotter(final FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror, PersistentIndexedCache<HashCode, HashCode> signatureCache, PersistentIndexedCache<HashCode, HashCode> compileSignatureCache) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new CompileClasspathSnapshotterProcessor(hasher, signatureCache, compileSignatureCache));
    }

    private static class CompileClasspathSnapshotterProcessor implements Factory<Processor<FileDetails, FileDetails>> {
        private final FileHasher hasher;
        private final PersistentIndexedCache<HashCode, HashCode> persistentCache;
        private final PersistentIndexedCache<HashCode, HashCode> compileSignatureCache;

        public CompileClasspathSnapshotterProcessor(FileHasher hasher, PersistentIndexedCache<HashCode, HashCode> runtimeSignatureCache, PersistentIndexedCache<HashCode, HashCode> compileSignatureCache) {
            this.hasher = hasher;
            this.persistentCache = runtimeSignatureCache;
            this.compileSignatureCache = compileSignatureCache;
        }

        @Override
        public Processor<FileDetails, FileDetails> create() {
            HashClassSignaturesFactory hashClassSignaturesFactory = new HashClassSignaturesFactory(compileSignatureCache);
            Processor<FileDetails, FileDetails> start = Processors.identity();
            Publisher<FileDetails> regularFiles = start.filter(Specs.negate(IS_ROOT_JAR_FILE))
                .map(new PublishingClasspathSnaphotter.PhysicalSnapshot())
                .subscribe(hashClassSignaturesFactory.create());
            Publisher<FileDetails> rootJarFiles = start.filter(IS_ROOT_JAR_FILE);

            Processor<FileDetails, FileDetails> jarContentsHashed =
                rootJarFiles.subscribe(cache(hashZipContents(hasher)))
                    .subscribe(compileCache(hashClassSignaturesInJar(hasher, hashClassSignaturesFactory)));

            return compose(start, regularFiles.join(jarContentsHashed).subscribe(sort()));
        }

        private <S extends FileDetails> Processor<S, FileDetails> cache(Processor<S, FileDetails> processor) {
            return new CachingProcessor<S>(persistentCache, processor);
        }

        private <S extends FileDetails> Processor<S, FileDetails> compileCache(Processor<S, FileDetails> processor) {
            return new CachingProcessor<S>(compileSignatureCache, processor);
        }
    }

    private static Processor<FileDetails, FileDetails> hashClassSignaturesInJar(FileHasher hasher, HashClassSignaturesFactory hashClassSignaturesFactory) {
        Processor<FileDetails, FileDetails> start = Processors.identity();
        Publisher<FileDetails> end = expandZip(hasher, start).flatMap(new CombineClassSignatures(hashClassSignaturesFactory));
        return compose(start, end);
    }

    public static class HashClassSignaturesFactory implements Factory<Processor<SnapshottableFileDetails, FileDetails>> {
        private final PersistentIndexedCache<HashCode, HashCode> signatureCache;
        private final ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());

        private HashClassSignaturesFactory(PersistentIndexedCache<HashCode, HashCode> signatureCache) {
            this.signatureCache = signatureCache;
        }

        @Override
        public Processor<SnapshottableFileDetails, FileDetails> create() {
            Processor<SnapshottableFileDetails, SnapshottableFileDetails> start = Processors.identity();
            Publisher<FileDetails> end = start
                .filter(IS_CLASS_FILE)
                .subscribe(new HashAbiProcessor(extractor));
            return new CachingProcessor<SnapshottableFileDetails>(signatureCache, compose(start, end));
        }
    }

    public static class CombineClassSignatures implements Function<GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails>, Publisher<FileDetails>> {
        private final HashClassSignaturesFactory hashClassSignaturesFactory;

        public CombineClassSignatures(HashClassSignaturesFactory hashClassSignaturesFactory) {
            this.hashClassSignaturesFactory = hashClassSignaturesFactory;
        }

        @Override
        public Publisher<FileDetails> apply(GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails> input) {
            return input.subscribe(hashClassSignaturesFactory.create()).subscribe(sort()).subscribe(new CombineHashes(input.getKey()));
        }
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }
}

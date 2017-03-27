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
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Predicate;
import io.reactivex.observables.GroupedObservable;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.Collections;

import static org.gradle.api.internal.changedetection.state.PublishingClasspathSnaphotter.*;

public class PublishingCompileClasspathSnapshotter extends PublishingFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    public static final Predicate<FileDetails> IS_CLASS_FILE = new Predicate<FileDetails>() {
        @Override
        public boolean test(FileDetails element) {
            return FileUtils.isClass(element.getName()) &&  element.getType() == FileType.RegularFile;
        }
    };

    public PublishingCompileClasspathSnapshotter(final FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror, PersistentIndexedCache<HashCode, HashCode> signatureCache, PersistentIndexedCache<HashCode, HashCode> compileSignatureCache) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new CompileClasspathSnapshotterProcessor(hasher, signatureCache, compileSignatureCache));
    }

    private static class CompileClasspathSnapshotterProcessor implements ObservableTransformer<FileDetails, FileDetails> {
        private final FileHasher hasher;
        private final PersistentIndexedCache<HashCode, HashCode> persistentCache;
        private final PersistentIndexedCache<HashCode, HashCode> compileSignatureCache;

        public CompileClasspathSnapshotterProcessor(FileHasher hasher, PersistentIndexedCache<HashCode, HashCode> runtimeSignatureCache, PersistentIndexedCache<HashCode, HashCode> compileSignatureCache) {
            this.hasher = hasher;
            this.persistentCache = runtimeSignatureCache;
            this.compileSignatureCache = compileSignatureCache;
        }

        @Override
        public Observable<FileDetails> apply(Observable<FileDetails> input) {
            HashClassSignaturesTransformer hashClassSignaturesTransformer = new HashClassSignaturesTransformer(compileSignatureCache);
            Observable<FileDetails> classFiles = input.filter(IS_CLASS_FILE)
                .map(new PhysicalSnapshot())
                .compose(hashClassSignaturesTransformer);
            Observable<FileDetails> rootJarFiles = input.filter(IS_ROOT_JAR_FILE);

            Observable<FileDetails> jarContentsHashed =
                rootJarFiles.compose(hashZipContents(hasher)).compose(hashSignaturesInJar(hashClassSignaturesTransformer));

            return Observable.concat(classFiles, jarContentsHashed);
        }

        private ObservableTransformer<? super FileDetails, ? extends FileDetails> hashSignaturesInJar(final HashClassSignaturesTransformer hashClassSignaturesTransformer) {
            return new ObservableTransformer<FileDetails, FileDetails>() {
                @Override
                public ObservableSource<FileDetails> apply(Observable<FileDetails> upstream) {
                    Observable<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>> expanded = upstream.map(new PhysicalSnapshot()).flatMap(expandZip(hasher));
                    return expanded.flatMap(hashClassSignaturesInJar(hashClassSignaturesTransformer));
                }
            };
        }

        private io.reactivex.functions.Function<? super GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>, Observable<FileDetails>> hashClassSignaturesInJar(final HashClassSignaturesTransformer hashClassSignaturesTransformer) {
            return new io.reactivex.functions.Function<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>, Observable<FileDetails>>() {
                @Override
                public Observable<FileDetails> apply(GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails> groupedObservable) throws Exception {
                    return groupedObservable.compose(hashClassSignaturesTransformer).toList().map(combineHashes(groupedObservable.getKey())).toObservable();
                }
            };
        }
    }

    public static class HashClassSignaturesTransformer implements ObservableTransformer<SnapshottableFileDetails, FileDetails> {
        private final PersistentIndexedCache<HashCode, HashCode> signatureCache;
        private final ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());

        private HashClassSignaturesTransformer(PersistentIndexedCache<HashCode, HashCode> signatureCache) {
            this.signatureCache = signatureCache;
        }

        @Override
        public ObservableSource<FileDetails> apply(Observable<SnapshottableFileDetails> upstream) {
            return upstream
                .filter(IS_CLASS_FILE)
                .flatMapIterable(new HashAbiFunction(extractor)).sorted(FILE_DETAILS_COMPARATOR);
        }
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }
}

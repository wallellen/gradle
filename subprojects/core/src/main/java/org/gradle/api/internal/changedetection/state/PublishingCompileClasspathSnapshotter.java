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
            return FileUtils.isClass(element.getName()) && element.getType() == FileType.RegularFile;
        }
    };

    public PublishingCompileClasspathSnapshotter(final FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror, PersistentIndexedCache<HashCode, HashCode> signatureCache, PersistentIndexedCache<HashCode, HashCode> compileSignatureCache) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new CompileClasspathSnapshotterProcessor(hasher, signatureCache, compileSignatureCache));
    }

    private static class CompileClasspathSnapshotterProcessor implements ObservableTransformer<FileDetails, FileDetails> {
        private final ObservableTransformer<FileDetails, FileDetails> hashZipContents;
        private final HashClassSignaturesTransformer hashClassSignaturesTransformer;
        private final ObservableTransformer<FileDetails, FileDetails> hashSignatures;

        public CompileClasspathSnapshotterProcessor(FileHasher hasher, PersistentIndexedCache<HashCode, HashCode> runtimeSignatureCache, PersistentIndexedCache<HashCode, HashCode> compileSignatureCache) {
            hashZipContents = hashZipContents(PublishingClasspathSnaphotter.<FileDetails, SnapshottableFileDetails>identity(), hasher, runtimeSignatureCache);
            hashClassSignaturesTransformer = new HashClassSignaturesTransformer();
            hashSignatures = hashZipContents(hashClassSignaturesTransformer, hasher, compileSignatureCache);
        }

        @Override
        public Observable<FileDetails> apply(Observable<FileDetails> input) {
            Observable<FileDetails> classFiles = input.filter(IS_CLASS_FILE)
                .map(new PhysicalSnapshot())
                .compose(hashClassSignaturesTransformer);
            Observable<FileDetails> rootJarFiles = input.filter(IS_ROOT_JAR_FILE);

            Observable<FileDetails> jarContentsHashed =
                rootJarFiles
                    .compose(hashZipContents)
                    .compose(hashSignatures);

            return Observable.concat(classFiles, jarContentsHashed);
        }
    }

    public static class HashClassSignaturesTransformer implements ObservableTransformer<SnapshottableFileDetails, FileDetails> {
        private final ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());

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

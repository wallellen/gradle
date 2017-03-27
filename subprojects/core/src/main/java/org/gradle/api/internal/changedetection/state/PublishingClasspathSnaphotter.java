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

import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.functions.Functions;
import io.reactivex.observables.GroupedObservable;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.FileUtils;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class PublishingClasspathSnaphotter extends PublishingFileCollectionSnapshotter implements ClasspathSnapshotter {
    public static final Ordering<FileDetails> FILE_DETAILS_COMPARATOR = Ordering.from(new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    });
    public static final Predicate<FileDetails> IS_ROOT_JAR_FILE = new Predicate<FileDetails>() {
        @Override
        public boolean test(FileDetails element) {
            return FileUtils.isJar(element.getName()) && element.isRoot() && element.getType() == FileType.RegularFile;
        }
    };

    public static final Predicate<FileDetails> IS_NOT_ROOT_JAR_FILE = new Predicate<FileDetails>() {
        @Override
        public boolean test(FileDetails element) throws Exception {
            return !IS_ROOT_JAR_FILE.test(element);
        }
    };

    public PublishingClasspathSnaphotter(final FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror, PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new ClasspathSnapshotterProcessorFactory(persistentCache, hasher)
        );
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return ClasspathSnapshotter.class;
    }


    public static class ClasspathSnapshotterProcessorFactory implements ObservableTransformer<FileDetails, FileDetails> {
        private final PersistentIndexedCache<HashCode, HashCode> persistentCache;
        private final FileHasher hasher;

        public ClasspathSnapshotterProcessorFactory(PersistentIndexedCache<HashCode, HashCode> persistentCache, FileHasher hasher) {
            this.persistentCache = persistentCache;
            this.hasher = hasher;
        }

        public Observable<FileDetails> apply(Observable<FileDetails> upstream) {
            Observable<FileDetails> regularFiles = upstream.filter(IS_NOT_ROOT_JAR_FILE);
            Observable<FileDetails> rootJarFiles = upstream.filter(IS_ROOT_JAR_FILE);

            Observable<FileDetails> jarContentsHashed =
                rootJarFiles.compose(hashZipContents(hasher));
            return Observable.concat(regularFiles, jarContentsHashed);
        }
    }

    public static ObservableTransformer<FileDetails, FileDetails> hashZipContents(final FileHasher hasher) {
        return new ObservableTransformer<FileDetails, FileDetails>() {
            @Override
            public ObservableSource<FileDetails> apply(Observable<FileDetails> upstream) {
                Observable<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>> expanded = upstream.map(new PhysicalSnapshot()).flatMap(expandZip(hasher));
                return expanded.flatMap(sortAndCombineHashes());
            }
        };
    }

    private static Function<? super GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>, Observable<FileDetails>> sortAndCombineHashes() {
        return new Function<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>, Observable<FileDetails>>() {
            @Override
            public Observable<FileDetails> apply(GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails> groupedObservable) throws Exception {
                return groupedObservable.sorted(FILE_DETAILS_COMPARATOR).toList().map(combineHashes(groupedObservable.getKey())).toObservable();
            }
        };
    }

    public static Function<? super List<? extends FileDetails>, FileDetails> combineHashes(final SnapshottableFileDetails key) {
        return new Function<List<? extends FileDetails>, FileDetails>() {
            @Override
            public FileDetails apply(List<? extends FileDetails> snapshottableFileDetails) throws Exception {
                HashCode newHash = hash(snapshottableFileDetails);
                return key.withContentHash(newHash);
            }

            private HashCode hash(Iterable<? extends FileDetails> details) {
                Hasher hasher = Hashing.md5().newHasher();
                for (FileDetails detail : details) {
                    hasher.putBytes(detail.getContent().getContentMd5().asBytes());
                }
                return hasher.hash();
            }
        };
    }

    private static <T extends FileDetails> Function<List<T>, List<T>> sort() {
        return new Function<List<T>, List<T>>() {
            @Override
            public List<T> apply(List<T> ts) throws Exception {
                return FILE_DETAILS_COMPARATOR.immutableSortedCopy(ts);
            }
        };
    }

    public static Function<? super SnapshottableFileDetails, Observable<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>>> expandZip(final FileHasher hasher) {
        return new Function<SnapshottableFileDetails, Observable<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>>>() {
            @Override
            public Observable<GroupedObservable<SnapshottableFileDetails, SnapshottableFileDetails>> apply(final SnapshottableFileDetails fileDetails) throws Exception {
                return Observable.generate(
                    new Callable<ExpandZipState>() {
                        @Override
                        public ExpandZipState call() throws Exception {
                            return new ExpandZipState(fileDetails, hasher);
                        }
                    },
                    new ExpandZip(), new Consumer<ExpandZipState>() {
                        @Override
                        public void accept(ExpandZipState expandZipState) throws Exception {
                            IoActions.closeQuietly(expandZipState.getInputStream());
                        }
                    }).groupBy(Functions.justFunction(fileDetails));
            }
        };
    }

    public static class PhysicalSnapshot implements Function<FileDetails, SnapshottableFileDetails> {
        @Override
        public SnapshottableFileDetails apply(FileDetails input) {
            return new DefaultPhysicalFileDetails(input);
        }
    }

    private static class ExpandZipState {
        private final FileHasher hasher;
        private final ZipInputStream inputStream;

        public ExpandZipState(SnapshottableFileDetails zipFile, FileHasher hasher) {
            this.hasher = hasher;
            this.inputStream = new ZipInputStream(zipFile.open());
        }
        public FileHasher getHasher() {
            return hasher;
        }

        public ZipInputStream getInputStream() {
            return inputStream;
        }
    }

    private static class ExpandZip implements BiConsumer<ExpandZipState, Emitter<SnapshottableFileDetails>> {
        @Override
        public void accept(ExpandZipState state, Emitter<SnapshottableFileDetails> emitter) throws Exception {
            try {
                ZipEntry zipEntry;
                ZipInputStream zipInputStream = state.getInputStream();
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if (zipEntry.isDirectory()) {
                        continue;
                    }
                    break;
                }
                if (zipEntry == null) {
                    emitter.onComplete();
                    return;
                }
                byte[] contents = ByteStreams.toByteArray(zipInputStream);
                emitter.onNext(
                    new ZipSnapshottableFileDetails(zipEntry, contents, state.getHasher().hash(new ByteArrayInputStream(contents)))
                );
            } catch (Exception e) {
                // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
                emitter.onError(e);
            }
        }
    }
}

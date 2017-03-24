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
import org.gradle.api.internal.changedetection.state.streams.GroupedPublisher;
import org.gradle.api.internal.changedetection.state.streams.Publisher;
import org.gradle.api.internal.changedetection.state.streams.Publishers;
import org.gradle.api.internal.changedetection.state.streams.SortingProcessor;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.util.Comparator;

import static org.gradle.api.internal.changedetection.state.streams.Publishers.flatten;
import static org.gradle.api.internal.changedetection.state.streams.Publishers.join;
import static org.gradle.api.internal.changedetection.state.streams.Publishers.map;

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
            return FileUtils.isJar(element.getName()) && element.isRoot();
        }
    };

    public PublishingClasspathSnaphotter(final FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror,
            new Function<Publisher<FileDetails>, Publisher<FileDetails>>() {
                @Override
                public Publisher<FileDetails> apply(Publisher<FileDetails> publisher) {
                    Publisher<FileDetails> regularFiles = Publishers.filter(publisher, Specs.negate(IS_ROOT_JAR_FILE));
                    Publisher<FileDetails> rootJarFiles = Publishers.filter(publisher, IS_ROOT_JAR_FILE);

                    Publisher<FileDetails> jarContentsHashed =
                        flatten(
                        map(
                            map(rootJarFiles, new PhysicalSnapshot()).subscribe(new ExpandZipProcessor(hasher)), new HashJarContents()));
                    return join(regularFiles, jarContentsHashed);
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

    private static class HashJarContents implements Function<GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails>, Publisher<FileDetails>> {
        @Override
        public Publisher<FileDetails> apply(GroupedPublisher<SnapshottableFileDetails, SnapshottableFileDetails> input) {
            return map(input, cleanup()).subscribe(sort()).subscribe(new CombineHashes(input.getKey()));
        }

        private SortingProcessor<FileDetails> sort() {
            return new SortingProcessor<FileDetails>(FILE_DETAILS_COMPARATOR);
        }

        private PublishingFileCollectionSnapshotter.CleanupFileDetails cleanup() {
            return new CleanupFileDetails();
        }
    }
}

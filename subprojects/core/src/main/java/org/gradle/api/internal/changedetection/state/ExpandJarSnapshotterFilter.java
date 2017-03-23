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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.DeprecationLogger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import static org.gradle.internal.nativeintegration.filesystem.FileType.RegularFile;

public class ExpandJarSnapshotterFilter implements SnapshotterFilter {
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR = new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };

    private final FileHasher hasher;
    private final SnapshotterFilter delegate;

    public ExpandJarSnapshotterFilter(FileHasher hasher, SnapshotterFilter delegate) {
        this.hasher = hasher;
        this.delegate = delegate;
    }

    @Override
    public Iterable<FileDetails> filter(Iterable<SnapshottableFileDetails> details) {
        ImmutableSortedSet.Builder<FileDetails> result = ImmutableSortedSet.orderedBy(FILE_DETAILS_COMPARATOR);
        for (SnapshottableFileDetails fileDetails : details) {
            if (fileDetails.getType() == FileType.Directory || fileDetails.getType() == FileType.Missing) {
                continue;
            }
            if (fileDetails.isRoot() && FileUtils.isJar(fileDetails.getName())) {
                Collection<FileDetails> expandedZip = expandZip(fileDetails);
                result.add(fileDetails.withContentHash(hash(expandedZip)));
            } else {
                result.addAll(delegate.filter(Collections.singleton(fileDetails)));
            }
        }
        return result.build();
    }

    private HashCode hash(Iterable<FileDetails> details) {
        Hasher hasher = Hashing.md5().newHasher();
        for (FileDetails detail : details) {
            hasher.putBytes(detail.getContent().getContentMd5().asBytes());
        }
        return hasher.hash();
    }


    private Collection<FileDetails> expandZip(SnapshottableFileDetails fileDetails) {
        File jarFilePath = new File(fileDetails.getPath());
        ImmutableSortedSet.Builder<FileDetails> expandedZip = ImmutableSortedSet.orderedBy(FILE_DETAILS_COMPARATOR);
        ZipInputStream zipInput = null;
        try {
            zipInput = new ZipInputStream(new FileInputStream(jarFilePath));

            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                byte[] contents = ByteStreams.toByteArray(zipInput);
                expandedZip.addAll(delegate.filter(Collections.<SnapshottableFileDetails>singleton(
                    new ZipSnapshottableFileDetails(fileDetails, zipEntry, contents, hasher.hash(new ByteArrayInputStream(contents))))));
            }
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            return filterMalformedJar(fileDetails);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + fileDetails.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            return filterMalformedJar(fileDetails);
        } finally {
            IOUtils.closeQuietly(zipInput);
        }
        return expandedZip.build();
    }

    private Collection<FileDetails> filterMalformedJar(SnapshottableFileDetails fileDetails) {
        DeprecationLogger.nagUserWith("Malformed jar [" + fileDetails.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        return Collections.<FileDetails>singletonList(fileDetails);
    }

    static class ZipSnapshottableFileDetails extends DefaultFileDetails implements SnapshottableFileDetails {
        private final byte[] contents;

        ZipSnapshottableFileDetails(SnapshottableFileDetails jarFile, ZipEntry entry, byte[] contents, HashCode contentHash) {
            super(jarFile.getPath() + "/" + entry.getName(),
                new RelativePath(true, entry.getName()), RegularFile, false, new FileHashSnapshot(contentHash));
            this.contents = contents;
        }

        @Override
        public InputStream open() {
            return new ByteArrayInputStream(contents);
        }
    }
}

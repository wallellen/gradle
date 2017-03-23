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
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import static org.gradle.internal.nativeintegration.filesystem.FileType.RegularFile;

public class ClasspathSnapshotterConfiguration extends SnapshotterConfiguration {
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR = new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };

    private final FileHasher hasher;

    public ClasspathSnapshotterConfiguration(FileHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public Iterable<FileDetails> mapTree(Iterable<FileDetails> fileTree) {
        ImmutableSortedSet.Builder<FileDetails> result = ImmutableSortedSet.orderedBy(FILE_DETAILS_COMPARATOR);
        for (FileDetails fileDetails : fileTree) {
            if (fileDetails.getType() == FileType.Directory || fileDetails.getType() == FileType.Missing) {
                continue;
            }
            if (fileDetails.isRoot() && FileUtils.isJar(fileDetails.getName())) {
                result.addAll(expandZip(fileDetails));
            } else {
                result.add(fileDetails);
            }
        }
        return result.build();
    }

    private List<FileDetails> expandZip(FileDetails fileDetails) {
        File jarFilePath = new File(fileDetails.getPath());
        List<FileDetails> expandedZip = new ArrayList<FileDetails>();
        ZipInputStream zipInput = null;
        try {
            zipInput = new ZipInputStream(new FileInputStream(jarFilePath));

            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                HashCode hash = hasher.hash(zipInput);
                expandedZip.add(new DefaultFileDetails(fileDetails.getPath() + "/" + zipEntry.getName(),
                    new RelativePath(true, zipEntry.getName()), RegularFile, false,
                    new FileHashSnapshot(hash)));
            }
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            return Collections.singletonList(fileDetails);
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + fileDetails.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            return Collections.singletonList(fileDetails);
        } finally {
            IOUtils.closeQuietly(zipInput);
        }
        return expandedZip;
    }

    @Override
    public FileDetails mapSingleSnapshot(FileDetails fileDetails) {
        return fileDetails;
    }
}

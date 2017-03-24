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
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.util.DeprecationLogger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import static org.gradle.internal.nativeintegration.filesystem.FileType.RegularFile;

public class ExpandJarMapper implements User.Observer<SnapshottableFileDetails> {
    private final FileHasher hasher;

    public ExpandJarMapper(FileHasher hasher, User.Observer<? super SnapshottableFileDetails> elementConsumer) {
        this.hasher = hasher;
        this.elementConsumer = elementConsumer;
    }

    private final User.Observer<? super SnapshottableFileDetails> elementConsumer;

    @Override
    public void onNext(SnapshottableFileDetails fileDetails) {
        File jarFilePath = new File(fileDetails.getPath());
        ZipInputStream zipInput = null;
        try {
            zipInput = new ZipInputStream(new FileInputStream(jarFilePath));

            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                byte[] contents = ByteStreams.toByteArray(zipInput);
                elementConsumer.onNext(new ZipSnapshottableFileDetails(fileDetails, zipEntry, contents, hasher.hash(new ByteArrayInputStream(contents))));
            }
        } catch (ZipException e) {
            // ZipExceptions point to a problem with the Zip, we try to be lenient for now.
            filterMalformedJar(fileDetails);
            return;
        } catch (IOException e) {
            // IOExceptions other than ZipException are failures.
            throw new UncheckedIOException("Error snapshotting jar [" + fileDetails.getName() + "]", e);
        } catch (Exception e) {
            // Other Exceptions can be thrown by invalid zips, too. See https://github.com/gradle/gradle/issues/1581.
            filterMalformedJar(fileDetails);
            return;
        } finally {
            IOUtils.closeQuietly(zipInput);
        }
        elementConsumer.onCompleted();
    }

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError() {
    }

    private void filterMalformedJar(SnapshottableFileDetails fileDetails) {
        DeprecationLogger.nagUserWith("Malformed jar [" + fileDetails.getName() + "] found on classpath. Gradle 5.0 will no longer allow malformed jars on a classpath.");
        elementConsumer.onError();
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

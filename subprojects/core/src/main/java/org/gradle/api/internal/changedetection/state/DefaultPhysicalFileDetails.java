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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class DefaultPhysicalFileDetails implements SnapshottableFileDetails {

    public FileDetails getDelegate() {
        return delegate;
    }

    private final FileDetails delegate;

    public DefaultPhysicalFileDetails(FileDetails delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isRoot() {
        return delegate.isRoot();
    }

    @Override
    public RelativePath getRelativePath() {
        return delegate.getRelativePath();
    }

    @Override
    public IncrementalFileSnapshot getContent() {
        return delegate.getContent();
    }

    @Override
    public FileType getType() {
        return delegate.getType();
    }

    @Override
    public FileDetails withContentHash(HashCode contentHash) {
        return delegate.withContentHash(contentHash);
    }

    @Override
    public InputStream open() {
        try {
            return new FileInputStream(new File(getPath()));
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }
}

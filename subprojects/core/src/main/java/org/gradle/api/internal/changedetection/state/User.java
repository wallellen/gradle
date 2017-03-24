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
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class User {
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR = new Comparator<FileDetails>() {
        @Override
        public int compare(FileDetails o1, FileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };
    public FileDetails snapshot(SnapshottableFileDetails fileDetails) {

        Receiver receiver = new Receiver();
        new ExpandJarMapper(null, new SortedCombiner(fileDetails, receiver)).onNext(fileDetails);
        return receiver.fileDetails;
    }

    private class Receiver implements Action<FileDetails> {
        private FileDetails fileDetails;

        @Override
        public void execute(FileDetails fileDetails) {
            this.fileDetails = fileDetails;
        }
    }

    public interface Observer<T> {
        void onNext(T object);
        void onCompleted();
        void onError();
    }

    private class SortedCombiner implements Observer<FileDetails> {
        private final SnapshottableFileDetails fileDetails;
        private final Receiver receiver;
        private final List<FileDetails> details = new ArrayList<FileDetails>();

        public SortedCombiner(SnapshottableFileDetails fileDetails, Receiver receiver) {
            this.fileDetails = fileDetails;
            this.receiver = receiver;
        }

        @Override
        public void onNext(FileDetails fileDetails) {
            details.add(fileDetails);
        }

        public void onCompleted() {
            Collections.sort(details, FILE_DETAILS_COMPARATOR);
            receiver.execute(fileDetails.withContentHash(hash(details)));
        }

        @Override
        public void onError() {
            details.clear();
            receiver.execute(fileDetails);
        }

        private HashCode hash(Iterable<FileDetails> details) {
            Hasher hasher = Hashing.md5().newHasher();
            for (FileDetails detail : details) {
                hasher.putBytes(detail.getContent().getContentMd5().asBytes());
            }
            return hasher.hash();
        }
    }
}

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

package org.gradle.internal.resources;

import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTrackedResourceLock implements ResourceLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTrackedResourceLock.class);

    private final String displayName;

    private final Multimap<Long, ResourceLock> threadResourceLockMap;
    private final ResourceLockCoordinationService coordinationService;

    public AbstractTrackedResourceLock(String displayName, Multimap<Long, ResourceLock> threadResourceLockMap, ResourceLockCoordinationService coordinationService) {
        this.displayName = displayName;
        this.threadResourceLockMap = threadResourceLockMap;
        this.coordinationService = coordinationService;
    }

    @Override
    public boolean tryLock() {
        ResourceLockState resourceLockState = coordinationService.getCurrent();
        if (!hasResourceLock()) {
            if (acquireLock()) {
                LOGGER.debug("{}: acquired lock on {}", Thread.currentThread().getName(), displayName);
                threadResourceLockMap.put(Thread.currentThread().getId(), this);
                resourceLockState.registerLocked(this);
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public void unlock() {
        coordinationService.getCurrent();
        if (hasResourceLock()) {
            releaseLock();
            LOGGER.debug("{}: released lock on {}", Thread.currentThread().getName(), displayName);
            threadResourceLockMap.remove(Thread.currentThread().getId(), this);
            coordinationService.notifyStateChange();
        }
    }

    @Override
    public boolean isLocked() {
        coordinationService.getCurrent();
        return doIsLocked();
    }

    @Override
    public boolean hasResourceLock() {
        coordinationService.getCurrent();
        return doHasResourceLock();
    }

    abstract protected boolean acquireLock();

    abstract protected void releaseLock();

    abstract protected boolean doIsLocked();

    abstract protected boolean doHasResourceLock();

    @Override
    public String getDisplayName() {
        return displayName;
    }
}

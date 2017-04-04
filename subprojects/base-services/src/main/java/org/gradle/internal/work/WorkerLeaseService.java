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

package org.gradle.internal.work;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.resources.ResourceLock;

import java.util.concurrent.Executor;

public interface WorkerLeaseService extends WorkerLeaseRegistry, ProjectLeaseRegistry, Stoppable {
    /**
     * Returns an {@link Executor} that will run a given {@link Runnable} while the specified locks are being held, releasing
     * the locks upon completion.  {@link Executor#execute(Runnable)} will run the provided {@link Runnable} immediately and
     * synchronously.  Blocks until the specified locks can be obtained.
     */
    Executor withLocks(ResourceLock... locks);

    /**
     * Returns an {@link Executor} that will run a given {@link Runnable} while the specified locks are releases and then
     * reacquire the locks upon completion.  If the locks cannot be immediately reacquired, all worker leases will be released
     * the method will block until the locks are reacquired.  {@link Executor#execute(Runnable)} will run the provided {@link Runnable}
     * immediately and synchronously.
     */
    Executor withoutLocks(ResourceLock... locks);
}

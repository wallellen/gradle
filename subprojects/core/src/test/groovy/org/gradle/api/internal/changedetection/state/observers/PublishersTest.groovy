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

package org.gradle.api.internal.changedetection.state.observers

import com.google.common.base.Function
import spock.lang.Specification

class PublishersTest extends Specification {

    def "can map publisher"() {
        def publisher = Publishers.create([1, 2, 3])
        def subscriber = new CollectingSubscriber<Integer>()
        Publishers.map(publisher, { it + 1 } as Function<Integer, Integer>).subscribe(subscriber)

        when:
        publisher.publish()

        then:
        subscriber.getCollection() == [2, 3, 4]
    }
}

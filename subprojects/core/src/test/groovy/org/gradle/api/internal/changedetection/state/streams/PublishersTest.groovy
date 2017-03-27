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

package org.gradle.api.internal.changedetection.state.streams

import com.google.common.base.Function
import org.gradle.api.specs.Spec
import spock.lang.Specification

import static org.gradle.api.internal.changedetection.state.streams.Publishers.*

class PublishersTest extends Specification {

    def "can map publisher"() {
        def publisher = create([1, 2, 3])
        def subscriber = new CollectingSubscriber<Integer>()
        publisher.map({ it + 1 } as Function<Integer, Integer>).subscribe(subscriber)

        when:
        subscriber.request()

        then:
        subscriber.getCollection() == [2, 3, 4]
    }

    def "can filter"() {
        def publisher = create([1, 2, 3])
        def subscriber = new CollectingSubscriber<Integer>()
        publisher.filter({ it % 2 == 0 } as Spec<Integer>).subscribe(subscriber)

        when:
        subscriber.request()

        then:
        subscriber.getCollection() == [2]
    }

    def "can flatMap"() {
        def publisher = create([1, 2, 3])
        def subscriber = new CollectingSubscriber<Integer>()
        publisher.flatMap({ Integer i ->
            create((1..i))
        } as Function).subscribe(subscriber)

        when:
        subscriber.request()

        then:
        subscriber.getCollection() == [1, 1, 2, 1, 2, 3]
    }

    def "can join"() {
        def publisher1 = create([1, 2, 3])
        def publisher2 = create([5, 7])
        def subscriber = new CollectingSubscriber<Integer>()
        publisher1.join(publisher2).subscribe(subscriber)

        when:
        subscriber.request()

        then:
        subscriber.collection == [1, 2, 3, 5, 7]
    }

    def "filtering works in combination with join"() {
        def publisher = create([1, 2, 3, 4])
        def subscriber = new CollectingSubscriber<Integer>()
        publisher.filter { it % 2 == 0}.join(publisher.filter { it % 2 == 1 }).subscribe(subscriber)

        when:
        subscriber.request()

        then:
        subscriber.collection == [1, 2, 3, 4]
    }

    def "can use processors"() {
        def publisher = create([1, 2, 3, 4])
        def subscriber = new CollectingSubscriber<Integer>()

        def start = Processors.identity()
        def processor = Processors.compose(start, start.filter { it % 2 == 0}.join(publisher.filter { it % 2 == 1 }))
        publisher.subscribe(processor).subscribe(subscriber)

        when:
        subscriber.request()

        then:
        subscriber.collection == [1, 2, 3, 4]
    }
}

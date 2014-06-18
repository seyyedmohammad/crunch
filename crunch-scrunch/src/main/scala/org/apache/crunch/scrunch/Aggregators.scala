/*
 * *
 *  * Licensed to the Apache Software Foundation (ASF) under one
 *  * or more contributor license agreements.  See the NOTICE file
 *  * distributed with this work for additional information
 *  * regarding copyright ownership.  The ASF licenses this file
 *  * to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.apache.crunch.scrunch

import org.apache.crunch.fn.{Aggregators => JAgg}
import org.apache.crunch._
import com.google.common.collect.ImmutableList
import org.apache.hadoop.conf.Configuration
import java.lang.{Iterable => JIterable}
import scala.collection.JavaConversions
import com.twitter.algebird.Monoid

/**
 * Scrunch versions of the common Aggregator types from Crunch.
 */
object Aggregators {

  import scala.math.Ordering._

  def sum[T: Monoid]: Aggregator[T] = sumUsing(implicitly[Monoid[T]])

  def sumUsing[T](m: Monoid[T]): Aggregator[T] = new SimpleAggregator[T] {
    def reset {
      sum = m.zero
    }

    def update(next: T) {
      sum = m.plus(sum, next)
    }

    def results: JIterable[T] = {
      return ImmutableList.of(sum)
    }

    private var sum: T = m.zero
  }

  def max[T: Ordering] = new SimpleAggregator[T] {
    def reset {
      max = None
    }

    def update(next: T) {
      if (max.isEmpty || implicitly[Ordering[T]].lt(max.get, next)) {
        max = Some(next)
      }
    }

    def results: JIterable[T] = {
      return JavaConversions.asJavaIterable(max.toIterable)
    }

    private var max: Option[T] = None
  }

  def min[T: Ordering] = new SimpleAggregator[T] {
    def reset {
      min = None
    }

    def update(next: T) {
      if (min.isEmpty || implicitly[Ordering[T]].gt(min.get, next)) {
        min = Some(next)
      }
    }

    def results: JIterable[T] = {
      return JavaConversions.asJavaIterable(min.toIterable)
    }

    private var min: Option[T] = None
  }

  /**
   * Return the first {@code n} values (or fewer if there are fewer values than {@code n}).
   *
   * @param n The number of values to return
   * @return The newly constructed instance
   */
  def first[V](n: Int): Aggregator[V] = JAgg.FIRST_N(n)

  /**
   * Return the last {@code n} values (or fewer if there are fewer values than {@code n}).
   *
   * @param n The number of values to return
   * @return The newly constructed instance
   */
  def last[V](n: Int) = JAgg.LAST_N(n)

  /**
   * Concatenate strings, with a separator between strings. There
   * is no limits of length for the concatenated string.
   *
   * <p><em>Note: String concatenation is not commutative, which means the
   * result of the aggregation is not deterministic!</em></p>
   *
   * @param separator
   * the separator which will be appended between each string
   * @param skipNull
   * define if we should skip null values. Throw
   * NullPointerException if set to false and there is a null
   * value.
   * @return The newly constructed instance
   */
  def concat(separator: String, skipNull: Boolean) = JAgg.STRING_CONCAT(separator, skipNull)

  /**
   * Concatenate strings, with a separator between strings. You can specify
   * the maximum length of the output string and of the input strings, if
   * they are &gt; 0. If a value is &lt;= 0, there is no limit.
   *
   * <p>Any too large string (or any string which would made the output too
   * large) will be silently discarded.</p>
   *
   * <p><em>Note: String concatenation is not commutative, which means the
   * result of the aggregation is not deterministic!</em></p>
   *
   * @param separator
   * the separator which will be appended between each string
   * @param skipNull
   * define if we should skip null values. Throw
   * NullPointerException if set to false and there is a null
   * value.
   * @param maxOutputLength
   * the maximum length of the output string. If it's set &lt;= 0,
   * there is no limit. The number of characters of the output
   * string will be &lt; maxOutputLength.
   * @param maxInputLength
   * the maximum length of the input strings. If it's set <= 0,
   * there is no limit. The number of characters of the input string
   * will be &lt; maxInputLength to be concatenated.
   * @return The newly constructed instance
   */
  def concat(separator: String, skipNull: Boolean, maxOutputLength: Long, maxInputLength: Long) =
    JAgg.STRING_CONCAT(separator, skipNull, maxOutputLength, maxInputLength)

  /**
   * Collect the unique elements of the input, as defined by the {@code equals} method for
   * the input objects. No guarantees are made about the order in which the final elements
   * will be returned.
   *
   * @return The newly constructed instance
   */
  def unique[V]: Aggregator[V] = JAgg.UNIQUE_ELEMENTS()

  /**
   * Collect a sample of unique elements from the input, where 'unique' is defined by
   * the {@code equals} method for the input objects. No guarantees are made about which
   * elements will be returned, simply that there will not be any more than the given sample
   * size for any key.
   *
   * @param maximumSampleSize The maximum number of unique elements to return per key
   * @return The newly constructed instance
   */
  def sampleUnique(maximumSampleSize: Int) = JAgg.SAMPLE_UNIQUE_ELEMENTS(maximumSampleSize)

  /**
   * Apply separate aggregators to each component of a {@link Tuple2}.
   */
  def pair[V1, V2](a1: Aggregator[V1], a2: Aggregator[V2]): Aggregator[(V1, V2)] = {
    return new Aggregators.PairAggregator[V1, V2](a1, a2)
  }

  /**
   * Apply separate aggregators to each component of a {@link Tuple3}.
   */
  def trip[V1, V2, V3](a1: Aggregator[V1], a2: Aggregator[V2], a3: Aggregator[V3]): Aggregator[(V1, V2, V3)] = {
    return new Aggregators.TripAggregator[V1, V2, V3](a1, a2, a3)
  }

  /**
   * Apply separate aggregators to each component of a {@link Tuple4}.
   */
  def quad[V1, V2, V3, V4](a1: Aggregator[V1], a2: Aggregator[V2], a3: Aggregator[V3], a4: Aggregator[V4])
    : Aggregator[(V1, V2, V3, V4)] = {
    return new Aggregators.QuadAggregator[V1, V2, V3, V4](a1, a2, a3, a4)
  }

  /**
   * Base class for aggregators that do not require any initialization.
   */
  abstract class SimpleAggregator[T] extends Aggregator[T] {
    def initialize(conf: Configuration) {
    }
  }

  private abstract class ProductAggregator[T <: Product](aggs: Array[Aggregator[_]]) extends Aggregator[T] {
    def initialize(configuration: Configuration) {
      for (a <- aggs) {
        a.initialize(configuration)
      }
    }

    def reset {
      for (a <- aggs) {
        a.reset
      }
    }

    protected def updateTuple(t: T) {
      var i: Int = 0
      while (i < aggs.length) {
        aggs(i).asInstanceOf[Aggregator[Any]].update(t.productElement(i))
        i = i + 1
      }
    }

    protected def results[T](index: Int): Iterable[T] = {
      return JavaConversions.iterableAsScalaIterable(aggs(index).results.asInstanceOf[JIterable[T]])
    }
  }

  private class PairAggregator[A, B](val a1: Aggregator[A], val a2: Aggregator[B])
    extends ProductAggregator[(A, B)](Array(a1, a2)) {

    def update(value: (A, B)) {
      updateTuple(value)
    }

    def results: JIterable[(A, B)] = {
      return JavaConversions.asJavaIterable(results[A](0).zip(results[B](1)))
    }
  }

  private class TripAggregator[A, B, C](val a1: Aggregator[A], val a2: Aggregator[B], val a3: Aggregator[C])
    extends ProductAggregator[(A, B, C)](Array(a1, a2, a3)) {
    def update(value: (A, B, C)) {
      updateTuple(value)
    }

    def results: JIterable[(A, B, C)] = {
      return JavaConversions.asJavaIterable(
        results[A](0).zip(results[B](1)).zip(results[C](2))
          .map(x => (x._1._1, x._1._2, x._2)))
    }
  }

  private class QuadAggregator[A, B, C, D](val a1: Aggregator[A], val a2: Aggregator[B],
                                           val a3: Aggregator[C], val a4: Aggregator[D])
    extends ProductAggregator[(A, B, C, D)](Array(a1, a2, a3, a4)) {

    def update(value: (A, B, C, D)) {
      updateTuple(value)
    }

    def results: JIterable[(A, B, C, D)] = {
      return JavaConversions.asJavaIterable(
        (results[A](0).zip(results[B](1))).zip(results[C](2).zip(results[D](3)))
          .map(x => (x._1._1, x._1._2, x._2._1, x._2._2)))
    }
  }

}

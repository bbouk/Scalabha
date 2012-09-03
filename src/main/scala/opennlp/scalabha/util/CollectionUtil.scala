package opennlp.scalabha.util

import scala.collection.GenIterable
import scala.collection.GenIterableLike
import scala.collection.GenTraversable
import scala.collection.GenTraversableLike
import scala.collection.GenTraversableOnce
import scala.collection.IterableLike
import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.collection.immutable
import scala.collection.mutable.Builder
import scala.util.Random

object CollectionUtil {

  //////////////////////////////////////////////////////
  // toTuple2: (T,T)
  // toTuple3: (T,T,T)
  // toTuple4: (T,T,T,T)
  // toTuple5: (T,T,T,T,T)
  //   - Convert this sequence to a tuple
  //////////////////////////////////////////////////////

  class Enriched_toTuple_Seq[A](seq: Seq[A]) {
    def toTuple2 = seq match { case Seq(a, b) => (a, b); case _ => throw new AssertionError("Cannot convert sequence of length %s into Tuple2".format(seq.size)) }
    def toTuple3 = seq match { case Seq(a, b, c) => (a, b, c); case _ => throw new AssertionError("Cannot convert sequence of length %s into Tuple3".format(seq.size)) }
    def toTuple4 = seq match { case Seq(a, b, c, d) => (a, b, c, d); case _ => throw new AssertionError("Cannot convert sequence of length %s into Tuple4".format(seq.size)) }
    def toTuple5 = seq match { case Seq(a, b, c, d, e) => (a, b, c, d, e); case _ => throw new AssertionError("Cannot convert sequence of length %s into Tuple5".format(seq.size)) }
  }
  implicit def enrich_toTuple_Seq[A](seq: Seq[A]): Enriched_toTuple_Seq[A] =
    new Enriched_toTuple_Seq(seq)

  class Enriched_toTuple_Array[A](seq: Array[A]) {
    def toTuple2 = seq match { case Array(a, b) => (a, b); case _ => throw new AssertionError("Cannot convert array of length %s into Tuple2".format(seq.size)) }
    def toTuple3 = seq match { case Array(a, b, c) => (a, b, c); case _ => throw new AssertionError("Cannot convert array of length %s into Tuple3".format(seq.size)) }
    def toTuple4 = seq match { case Array(a, b, c, d) => (a, b, c, d); case _ => throw new AssertionError("Cannot convert array of length %s into Tuple4".format(seq.size)) }
    def toTuple5 = seq match { case Array(a, b, c, d, e) => (a, b, c, d, e); case _ => throw new AssertionError("Cannot convert array of length %s into Tuple5".format(seq.size)) }
  }
  implicit def enrich_toTuple_Array[A](seq: Array[A]): Enriched_toTuple_Array[A] =
    new Enriched_toTuple_Array(seq)

  //////////////////////////////////////////////////////
  // +:(elem: B): Iterator[B]
  //   - Prepend an element to the iterator
  // :+(elem: B): Iterator[B]
  //   - Append an element to the end of the iterator
  //////////////////////////////////////////////////////

  class Enriched_prependAppend_Iterator[A](self: Iterator[A]) {
    /**
     * Prepend an item to the front of the iterator
     *
     * @param elem	the item to be prepended
     * @return a new iterator
     */
    def +:[B >: A](elem: B): Iterator[B] =
      Iterator(elem) ++ self

    /**
     * Append an item to the end of the iterator
     *
     * @param elem	the item to be appended
     * @return a new iterator
     */
    def :+[B >: A](elem: B): Iterator[B] =
      self ++ Iterator(elem)
  }
  implicit def enrich_prependAppend_Iterator[A](self: Iterator[A]): Enriched_prependAppend_Iterator[A] =
    new Enriched_prependAppend_Iterator(self)

  //////////////////////////////////////////////////////
  // groupByKey(): Map[T,Repr[U]]
  //   - For a collection of pairs (k,v), create a map from each `k` to the  
  //     collection of `v`s with which it is associated.
  //   - Equivalent to self.groupBy(_._1).map { case (k, elems) => (k, elems.map(_._2)) }
  //////////////////////////////////////////////////////

  class Enriched_groupByKey_TraversableLike[A, Repr <: Traversable[A]](self: TraversableLike[A, Repr]) {
    /**
     * For a collection of pairs (k,v), create a map from each `k` to the
     * collection of `v`s with which it is associated.
     *
     * Equivalent to self.groupBy(_._1).map { case (k, elems) => (k, elems.map(_._2)) }
     *
     * @return Map from `k`s to collections of `v`s
     */
    def groupByKey[K, V, That](implicit ev: A <:< (K, V), bf: CanBuildFrom[Repr, V, That]): Map[K, That] = {
      val m = mutable.Map.empty[K, Builder[V, That]]
      for ((key, value) <- self.map(ev)) {
        val bldr = m.getOrElseUpdate(key, bf(self.asInstanceOf[Repr]))
        bldr += value
      }
      val b = immutable.Map.newBuilder[K, That]
      for ((k, v) <- m)
        b += ((k, v.result))
      b.result
    }
  }
  implicit def enrich_groupByKey_TraversableLike[A, Repr <: Traversable[A]](self: TraversableLike[A, Repr]): Enriched_groupByKey_TraversableLike[A, Repr] =
    new Enriched_groupByKey_TraversableLike(self)

  //////////////////////////////////////////////////////
  // ungroup(): Iterator[(A, B)]
  //   - For a map with collections for values, return an iterator of pairs
  //     where each key is paired with each item in its value collection
  //   - Equivalent to self.toIterator.flatMap { case (a, bs) => bs.toIterator.map(a -> _) }
  //////////////////////////////////////////////////////

  class Enriched_ungroup_GenTraversableOnce[A, B](self: GenTraversableOnce[(A, GenTraversableOnce[B])]) {
    /**
     * For a map with collections for values, return an iterator of pairs
     * where each key is paired with each item in its value collection.
     *
     * Equivalent to self.toIterator.flatMap { case (a, bs) => bs.toIterator.map(a -> _) }
     *
     * @return an iterator of pairs
     */
    def ungroup() = self.toIterator.flatMap { case (a, bs) => bs.toIterator.map(a -> _) }
  }
  implicit def enrich_ungroup_GenTraversableOnce[A, B](self: GenTraversableOnce[(A, GenTraversableOnce[B])]): Enriched_ungroup_GenTraversableOnce[A, B] =
    new Enriched_ungroup_GenTraversableOnce(self)

  //////////////////////////////////////////////////////
  // dropRightWhile(p: A => Boolean): Repr
  //////////////////////////////////////////////////////

  class Enriched_dropRightWhile_IterableLike[A, Repr <: Iterable[A]](self: IterableLike[A, Repr]) {
    def dropRightWhile[That](p: A => Boolean)(implicit bf: CanBuildFrom[Repr, A, That]): That = {
      val b = bf(self.asInstanceOf[Repr])
      val buffer = collection.mutable.Buffer[A]()
      for (x <- self) {
        buffer += x
        if (!p(x)) {
          b ++= buffer
          buffer.clear()
        }
      }
      b.result
    }
  }
  implicit def enrich_dropRightWhile_IterableLike[A, Repr <: Iterable[A]](self: IterableLike[A, Repr]): Enriched_dropRightWhile_IterableLike[A, Repr] =
    new Enriched_dropRightWhile_IterableLike(self)

  class Enriched_dropRightWhile_String(self: String) {
    def dropRightWhile(p: Char => Boolean): String = {
      val b = stringCanBuildFrom()
      val buffer = collection.mutable.Buffer[Char]()
      for (x <- self) {
        buffer += x
        if (!p(x)) {
          b ++= buffer
          buffer.clear()
        }
      }
      b.result
    }
  }
  implicit def enrich_dropRightWhile_String(self: String): Enriched_dropRightWhile_String =
    new Enriched_dropRightWhile_String(self)

  //////////////////////////////////////////////////////
  // mapKeys(f: T => R): Repr[(R,U)]
  //   - In a collection of pairs, map a function over the first item of each pair.
  //   - Functionally equivalent to:
  //         this.map{case (k,v) => f(k) -> v}
  //////////////////////////////////////////////////////

  class Enriched_mapKeys_GenTraversableLike[T, U, Repr <: GenTraversable[(T, U)]](self: GenTraversableLike[(T, U), Repr]) {
    /**
     * In a collection of pairs, map a function over the first item of each
     * pair.  Ensures that the map is computed at call-time, and not returned
     * as a view as 'Map.mapValues' would do.
     *
     * @param f	function to map over the first item of each pair
     * @return a collection of pairs
     */
    def mapKeys[R, That](f: T => R)(implicit bf: CanBuildFrom[Repr, (R, U), That]) = {
      val b = bf(self.asInstanceOf[Repr])
      b.sizeHint(self.size)
      for ((k, v) <- self) b += f(k) -> v
      b.result
    }
  }
  implicit def enrich_mapKeys_GenTraversableLike[T, U, Repr <: GenTraversable[(T, U)]](self: GenTraversableLike[(T, U), Repr]): Enriched_mapKeys_GenTraversableLike[T, U, Repr] =
    new Enriched_mapKeys_GenTraversableLike(self)

  class Enriched_mapKeys_Iterator[T, U](self: Iterator[(T, U)]) {
    /**
     * In a collection of pairs, map a function over the first item of each
     * pair.
     *
     * @param f	function to map over the first item of each pair
     * @return a collection of pairs
     */
    def mapKeys[R](f: T => R) = new Iterator[(R, U)] {
      def hasNext = self.hasNext
      def next() = {
        val (k, v) = self.next()
        f(k) -> v
      }
    }
  }
  implicit def enrich_mapKeys_Iterator[T, U](self: Iterator[(T, U)]): Enriched_mapKeys_Iterator[T, U] =
    new Enriched_mapKeys_Iterator(self)

  //////////////////////////////////////////////////////
  // mapVals(f: U => R): Repr[(T,R)]
  //   - In a collection of pairs, map a function over the second item of each pair.
  //   - Ensures that the map is computed at call-time, and not returned as a view as `Map.mapValues` would do.
  //   - Equivalent to: this.map { case (k,v) => k -> f(v) }
  //////////////////////////////////////////////////////

  class Enriched_mapVals_GenTraversableLike[T, U, Repr <: GenTraversable[(T, U)]](self: GenTraversableLike[(T, U), Repr]) {
    /**
     * In a collection of pairs, map a function over the second item of each
     * pair.  Ensures that the map is computed at call-time, and not returned
     * as a view as 'Map.mapValues' would do.
     *
     * @param f	function to map over the second item of each pair
     * @return a collection of pairs
     */
    def mapVals[R, That](f: U => R)(implicit bf: CanBuildFrom[Repr, (T, R), That]) = {
      val b = bf(self.asInstanceOf[Repr])
      b.sizeHint(self.size)
      for ((k, v) <- self) b += k -> f(v)
      b.result
    }
  }
  implicit def enrich_mapVals_GenTraversableLike[T, U, Repr <: GenTraversable[(T, U)]](self: GenTraversableLike[(T, U), Repr]): Enriched_mapVals_GenTraversableLike[T, U, Repr] =
    new Enriched_mapVals_GenTraversableLike(self)

  class Enriched_mapVals_Iterator[T, U](self: Iterator[(T, U)]) {
    /**
     * In a collection of pairs, map a function over the second item of each
     * pair.
     *
     * @param f	function to map over the second item of each pair
     * @return a collection of pairs
     */
    def mapVals[R](f: U => R) = new Iterator[(T, R)] {
      def hasNext = self.hasNext
      def next() = {
        val (k, v) = self.next()
        k -> f(v)
      }
    }
  }
  implicit def enrich_mapVals_Iterator[T, U](self: Iterator[(T, U)]): Enriched_mapVals_Iterator[T, U] =
    new Enriched_mapVals_Iterator(self)

  //////////////////////////////////////////////////////
  // avg(): A
  //   - Find the average (mean) of this collection of numbers
  //////////////////////////////////////////////////////

  class Enrich_avg_GenTraversableOnce[A](self: GenTraversableOnce[A]) {
    /**
     * Find the average (mean) of this collection of numbers.
     *
     * @return the average (mean)
     */
    def avg(implicit num: Fractional[A]) = {
      val (total, count) = self.toIterator.foldLeft((num.zero, num.zero)) {
        case ((total, count), x) => (num.plus(total, x), num.plus(count, num.one))
      }
      num.div(total, count)
    }
  }
  implicit def enrich_avg_GenTraversableOnce[A](self: GenTraversableOnce[A]): Enrich_avg_GenTraversableOnce[A] =
    new Enrich_avg_GenTraversableOnce(self)

  class Enrich_avg_Int_GenTraversableOnce(self: GenTraversableOnce[Int]) {
    /**
     * Find the average (mean) of this collection of numbers.
     *
     * @return the average (mean)
     */
    def avg = {
      val (total, count) = self.toIterator.foldLeft((0, 0)) {
        case ((total, count), x) => (total + x, count + 1)
      }
      total.toDouble / count
    }
  }
  implicit def enrich_avg_Int_GenTraversableOnce(self: GenTraversableOnce[Int]): Enrich_avg_Int_GenTraversableOnce =
    new Enrich_avg_Int_GenTraversableOnce(self)

  //////////////////////////////////////////////////////
  // normalize(): Repr[A]
  //   - Normalize this collection of numbers by dividing each by the sum
  //////////////////////////////////////////////////////

  class Enriched_normalize_GenTraversableLike[A, Repr <: GenTraversable[A]](self: GenTraversableLike[A, Repr]) {
    /**
     * Normalize this collection of numbers by dividing each by the sum
     *
     * @return normalized values
     */
    def normalize[That](implicit num: Fractional[A], bf: CanBuildFrom[Repr, A, That]) = {
      val b = bf(self.asInstanceOf[Repr])
      b.sizeHint(self.size)
      val total = self.sum
      for (x <- self) b += num.div(x, total)
      b.result
    }
  }
  implicit def enrich_normalize_GenTraversableLike[A, Repr <: GenTraversable[A]](self: GenTraversableLike[A, Repr]): Enriched_normalize_GenTraversableLike[A, Repr] =
    new Enriched_normalize_GenTraversableLike(self)

  class Enriched_normalize_Int_GenTraversableLike[Repr <: GenTraversable[Int]](self: GenTraversableLike[Int, Repr]) {
    /**
     * Normalize this collection of numbers by dividing each by the sum
     *
     * @return normalized values
     */
    def normalize[That](implicit bf: CanBuildFrom[Repr, Double, That]) = {
      val b = bf(self.asInstanceOf[Repr])
      b.sizeHint(self.size)
      val total = self.sum.toDouble
      for (x <- self) b += x / total
      b.result
    }
  }
  implicit def enrich_normalize_Int_GenTraversableLike[Repr <: GenTraversable[Int]](self: GenTraversableLike[Int, Repr]): Enriched_normalize_Int_GenTraversableLike[Repr] =
    new Enriched_normalize_Int_GenTraversableLike(self)

  //////////////////////////////////////////////////////
  // normalizeValues(): Repr[(T,U)]
  //   - Normalize this values in this collection of pairs
  //////////////////////////////////////////////////////

  class Enriched_normalizeValues_GenTraversableLike[T, U, Repr <: GenTraversable[(T, U)]](self: GenTraversableLike[(T, U), Repr]) {
    /**
     * Normalize this values in this collection of pairs
     *
     * @return a collection of pairs
     */
    def normalizeValues[That](implicit num: Fractional[U], bf: CanBuildFrom[Repr, (T, U), That]) = {
      val b = bf(self.asInstanceOf[Repr])
      b.sizeHint(self.size)
      val total = self.foldLeft(num.zero)((z, a) => num.plus(z, a._2))
      for ((k, v) <- self) b += k -> num.div(v, total)
      b.result
    }
  }
  implicit def enrich_normalizeValues_GenTraversableLike[T, U, Repr <: GenTraversable[(T, U)]](self: GenTraversableLike[(T, U), Repr]): Enriched_normalizeValues_GenTraversableLike[T, U, Repr] =
    new Enriched_normalizeValues_GenTraversableLike(self)

  class Enriched_normalizeValues_Int_GenTraversableLike[T, Repr <: GenTraversable[(T, Int)]](self: GenTraversableLike[(T, Int), Repr]) {
    /**
     * Normalize this values in this collection of pairs
     *
     * @return a collection of pairs
     */
    def normalizeValues[That](implicit bf: CanBuildFrom[Repr, (T, Double), That]) = {
      val b = bf(self.asInstanceOf[Repr])
      b.sizeHint(self.size)
      val total = self.foldLeft(0)((z, a) => z + a._2).toDouble
      for ((k, v) <- self) b += k -> (v / total)
      b.result
    }
  }
  implicit def enrich_normalizeValues_Int_GenTraversableLike[T, Repr <: GenTraversable[(T, Int)]](self: GenTraversableLike[(T, Int), Repr]): Enriched_normalizeValues_Int_GenTraversableLike[T, Repr] =
    new Enriched_normalizeValues_Int_GenTraversableLike(self)

  //////////////////////////////////////////////////////
  // Conversion (.toX) methods
  //////////////////////////////////////////////////////
  class Enriched_toVector_GenTraversableOnce[A](self: GenTraversableOnce[A]) {
    def toVector =
      if (self.isEmpty) Vector.empty[A]
      else Vector.newBuilder[A] ++= self.toIterator result
  }
  implicit def enrich_toVector_GenTraversableOnce[A](self: GenTraversableOnce[A]): Enriched_toVector_GenTraversableOnce[A] =
    new Enriched_toVector_GenTraversableOnce(self)
  implicit def addToVectorToArray[A](self: Array[A]): Enriched_toVector_GenTraversableOnce[A] =
    new Enriched_toVector_GenTraversableOnce(self)

}

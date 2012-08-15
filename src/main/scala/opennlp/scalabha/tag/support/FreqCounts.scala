package opennlp.scalabha.tag.support

import opennlp.scalabha.util.CollectionUtils._

/**
 * Convenience wrapper of a map from item pairs to their counts.  Its primary
 * function is to make adding counts easier.  The "++" operator adds the
 * counts for each respective entry (unlike Map's standard behavior).
 *
 * @tparam A	the conditioning item being counted; P(B|A).
 * @tparam B	the conditioned item being counted; P(B|A).
 * @tparam N	the Numeric type of the count
 */
class CondFreqCounts[A, B, N: Numeric](private val self: Map[A, Map[B, N]]) {
  def ++(that: Map[A, Map[B, N]]) = new CondFreqCounts((self.iterator ++ that.iterator).groupByKey.mapVals(_.reduce(_ +++ _)))
  def ++(that: CondFreqCounts[A, B, N]): CondFreqCounts[A, B, N] = this ++ that.toMap

  def iterator = self.iterator
  def map[C](f: ((A, Map[B, N])) => C) = iterator.map(f)
  def values = iterator.map(_._2)
  def toMap = self
  def toDoubles = CondFreqCounts(self.mapVals(_.mapVals(implicitly[Numeric[N]].toDouble)))
  override def toString = "CondFreqCounts(%s)".format(self)
}

object CondFreqCounts {
  def apply[A, B, N: Numeric]() = new CondFreqCounts[A, B, N](Map())
  def apply[A, B, N: Numeric](self: Map[A, Map[B, N]]) = new CondFreqCounts(self)
}

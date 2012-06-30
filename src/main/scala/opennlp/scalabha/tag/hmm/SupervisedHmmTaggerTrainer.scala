package opennlp.scalabha.tag.hmm

import scala.annotation.tailrec
import scala.io.Source
import opennlp.scalabha.tag._
import opennlp.scalabha.tag.support._
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.Pattern.{ +:, :+ }
import opennlp.scalabha.tag.SupervisedTaggerTrainer
import opennlp.scalabha.tag.UnsupervisedTaggerTrainer
import opennlp.scalabha.tag.hmm.support._
import org.apache.commons.logging.LogFactory
import opennlp.scalabha.util.LogNum
import opennlp.scalabha.util.LogNum._
import scala.collection.GenTraversableOnce
import scala.collection.mutable.ListBuffer

/**
 * Factory for training a Hidden Markov Model tagger directly from labeled data.
 *
 * @tparam Sym	visible symbols in the sequences
 * @tparam Tag	tags applied to symbols
 *
 * @param transitionCountsTransformer	factory for generating builders that count tag occurrences and compute distributions
 * @param emissionCountsTransformer		factory for generating builders that count symbol occurrences and compute distributions
 */
class SupervisedHmmTaggerTrainer[Sym, Tag](
  transitionCountsTransformer: TransitionCountsTransformer[Tag, Sym],
  emissionCountsTransformer: EmissionCountsTransformer[Tag, Sym])
  extends SupervisedTaggerTrainer[Sym, Tag] {

  private val LOG = LogFactory.getLog(classOf[SupervisedHmmTaggerTrainer[Sym, Tag]]);

  /**
   * Train a Hidden Markov Model tagger directly from labeled data.
   * Uses transition and emission counters to compute distributions based on labeled data.
   *
   * @param taggedTrainSequences	labeled sequences to use for training the model
   * @param tagDict					tag dictionary
   * @return						a trained tagger
   */
  override def trainSupervised(taggedTrainSequences: Iterable[IndexedSeq[(Sym, Tag)]], tagDict: TagDict[Sym, Tag]): Tagger[Sym, Tag] = {
    val (transitionCounts, emissionCounts) = getCountsFromTagged(taggedTrainSequences)
    val transitionDist = CondFreqDist(transitionCountsTransformer(transitionCounts))
    val emissionDist = CondFreqDist(emissionCountsTransformer(emissionCounts))
    new HmmTagger(transitionDist, emissionDist, OptionalTagDict(tagDict))
  }

  /**
   * Get transition and emission counts from labeled data
   *
   * @param taggedTrainSequences	labeled sequences from which to extract counts
   */
  protected def getCountsFromTagged(taggedSequences: Iterable[IndexedSeq[(Sym, Tag)]]) = {
    // Separate symbols from tags.  Add start/final symbols and tags to each sequence
    val endedSequences = taggedSequences.map(((None -> None) +: _.map { case (s, t) => Some(s) -> Some(t) } :+ (None -> None)))

    // Get the tag transitions, including start/final tags
    val tagPairs = endedSequences.map(_.map(_._2).sliding2).flatten
    val transitionCounts = tagPairs.groupByKey.mapVals(_.counts)

    // Get the word/tag pairs (emissions)
    val tagSymbolPairs = endedSequences.flatMap(_.map(_.swap))
    val emissionCounts = tagSymbolPairs.groupByKey.mapVals(_.counts)

    (transitionCounts, emissionCounts)
  }
}

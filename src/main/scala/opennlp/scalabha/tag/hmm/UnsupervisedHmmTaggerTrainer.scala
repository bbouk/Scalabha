package opennlp.scalabha.tag.hmm

import org.apache.commons.logging.LogFactory
import opennlp.scalabha.tag.support._
import opennlp.scalabha.tag._
import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.Probability._
import opennlp.scalabha.util.Pattern
import opennlp.scalabha.util.Pattern.{ -> }
import opennlp.scalabha.util.Probability
import scala.collection.GenIterable

/**
 * Factory for training a Hidden Markov Model tagger from a combination of
 * labeled data and unlabeled data using the Expectation-Maximization (EM)
 * algorithm.
 *
 * @tparam Sym	visible symbols in the sequences
 * @tparam Tag	tags applied to symbols
 *
 * @param initialUnsupervisedEmissionDist
 * @param estimatedTransitionCountsTransformer		factory for generating builders that count tag occurrences and compute distributions during EM
 * @param estimatedEmissionCountsTransformer		factory for generating builders that count symbol occurrences and compute distributions during EM
 * @param startEndSymbol							a unique start/end symbol used internally to mark the beginning and end of a sentence
 * @param startEndTag								a unique start/end tag used internally to mark the beginning and end of a sentence
 * @param maxIterations								maximum number of iterations to be run during EM
 * @param minAvgLogProbChangeForEM					stop iterating EM if change in average log probability is less than this threshold
 */
class UnsupervisedHmmTaggerTrainer[Sym, Tag](
  initialUnsupervisedEmissionDist: Tag => Sym => Probability,
  override protected val estimatedTransitionCountsTransformer: CondCountsTransformer[Tag, Tag],
  override protected val estimatedEmissionCountsTransformer: CondCountsTransformer[Tag, Sym],
  override protected val startEndSymbol: Sym,
  override protected val startEndTag: Tag,
  override protected val maxIterations: Int = 50,
  override protected val minAvgLogProbChangeForEM: Double = 0.00001)
  extends AbstractEmHmmTaggerTrainer[Sym, Tag]
  with UnsupervisedTaggerTrainer[Sym, Tag] {

  /**
   * Train a Hidden Markov Model tagger only on unlabeled data using the
   * Expectation-Maximization (EM) algorithm.
   *
   * @param tagDict					a mapping from symbols to their possible tags
   * @param rawTrainSequences		unlabeled sequences to be used as unsupervised training data
   * @return						a trained tagger
   */
  override def trainUnsupervised(tagDict: Map[Sym, Set[Tag]], rawTrainSequences: Iterable[IndexedSeq[Sym]]): Tagger[Sym, Tag] = {
    LOG.info("Beginning unsupervised training")
    LOG.info("Tag dict: %d symbols, %.3f avg tags/symbol".format(tagDict.size, tagDict.values.map(_.size.toDouble).avg))

    // Correct tag dictionary for start/final symbols
    val tagDictWithEnds = tagDict + (startEndSymbol -> Set(startEndTag))

    // Create the initial distributions
    val allTags = tagDictWithEnds.values.flatten.toSet
    LOG.debug("make initialTransitions")
    val initialTransitions = CondFreqDist(DefaultedCondFreqCounts(allTags.mapTo(_ => allTags.mapTo(_ => 1.0).toMap).toMap))
    val initialEmissions = initialUnsupervisedEmissionDist

    if (LOG.isDebugEnabled) {
      LOG.debug("    initialUnsupervisedEmissionDist")
      for (t <- List("NN", "N", "DT", "D").map(_.asInstanceOf[Tag]))
        for (w <- List("the", "company", "zzzzzzz").map(_.asInstanceOf[Sym]))
          if (initialUnsupervisedEmissionDist(t)(w) != Probability.zero)
            LOG.debug("        p(%s|%s) = %.2f (%.2f)".format(w, t, initialUnsupervisedEmissionDist(t)(w).toDouble, initialUnsupervisedEmissionDist(t)(w).underlying))
    }

    // Do not assume any known counts -- use only EM-estimated counts
    val initialTransitionCounts = CondFreqCounts[Tag, Tag, Double]()
    val initialEmissionCounts = CondFreqCounts[Tag, Sym, Double]()

    // Re-estimate probability distributions using EM
    val (transitions, emissions) =
      reestimateProbabilityDistributions(
        tagDictWithEnds, rawTrainSequences,
        initialTransitionCounts, initialEmissionCounts,
        initialTransitions, initialEmissions)

    // Construct the HMM tagger from the estimated probabilities
    new HmmTagger(transitions, emissions, tagDictWithEnds, startEndSymbol, startEndTag)
  }

}

/**
 * Factory for training a Hidden Markov Model tagger from a combination of
 * labeled data and unlabeled data using the Expectation-Maximization (EM)
 * algorithm.
 *
 * @tparam Sym	visible symbols in the sequences
 * @tparam Tag	tags applied to symbols
 *
 * @param initialTransitionCountsTransformer		factory for generating builders that count tag occurrences and compute distributions for input to EM
 * @param initialEmissionCountsTransformer			factory for generating builders that count symbol occurrences and compute distributions for input to EM
 * @param estimatedTransitionCountsTransformer		factory for generating builders that count tag occurrences and compute distributions during EM
 * @param estimatedEmissionCountsTransformer		factory for generating builders that count symbol occurrences and compute distributions during EM
 * @param startEndSymbol						a unique start/end symbol used internally to mark the beginning and end of a sentence
 * @param startEndTag							a unique start/end tag used internally to mark the beginning and end of a sentence
 * @param maxIterations							maximum number of iterations to be run during EM
 * @param minAvgLogProbChangeForEM				stop iterating EM if change in average log probability is less than this threshold
 */
class SemisupervisedHmmTaggerTrainer[Sym, Tag](
  initialTransitionCountsTransformer: CondCountsTransformer[Tag, Tag],
  initialEmissionCountsTransformer: CondCountsTransformer[Tag, Sym],
  override protected val estimatedTransitionCountsTransformer: CondCountsTransformer[Tag, Tag],
  override protected val estimatedEmissionCountsTransformer: CondCountsTransformer[Tag, Sym],
  override protected val startEndSymbol: Sym,
  override protected val startEndTag: Tag,
  override protected val maxIterations: Int = 50,
  override protected val minAvgLogProbChangeForEM: Double = 0.00001)
  extends SupervisedHmmTaggerTrainer[Sym, Tag](initialTransitionCountsTransformer, initialEmissionCountsTransformer, startEndSymbol, startEndTag)
  with AbstractEmHmmTaggerTrainer[Sym, Tag]
  with SemisupervisedTaggerTrainer[Sym, Tag] {

  /**
   * Train a Hidden Markov Model tagger from a combination of labeled data and
   * unlabeled data using the Expectation-Maximization (EM) algorithm.  Use
   * the provided tag dictionary instead of creating one from the labeled data.
   *
   * @param tagDict					a mapping from symbols to their possible tags
   * @param rawTrainSequences		unlabeled sequences to be used as unsupervised training data
   * @param taggedTrainSequences	labeled sequences to be used as supervised training data
   * @return						a trained tagger
   */
  override def trainSemisupervised(
    tagDict: Map[Sym, Set[Tag]],
    rawTrainSequences: Iterable[IndexedSeq[Sym]],
    taggedTrainSequences: Iterable[IndexedSeq[(Sym, Tag)]]): Tagger[Sym, Tag] = {

    // Get initial counts and probability distributions from the labeled data alone
    val (initialTransitionCounts, initialEmissionCounts) = getCountsFromTagged(taggedTrainSequences)

    // Correct tag dictionary for start/final symbols
    val tagDictWithEnds = tagDict + (startEndSymbol -> Set(startEndTag))

    // Create the initial distributions
    val initialTransitions = CondFreqDist(initialTransitionCountsTransformer(initialTransitionCounts))
    val initialEmissions = CondFreqDist(initialEmissionCountsTransformer(initialEmissionCounts))

    // Re-estimate probability distributions using EM
    val (transitions, emissions) =
      reestimateProbabilityDistributions(
        tagDictWithEnds, rawTrainSequences,
        CondFreqCounts(initialTransitionCounts).toDouble, CondFreqCounts(initialEmissionCounts).toDouble,
        initialTransitions, initialEmissions)

    // Construct the HMM tagger from the estimated probabilities
    new HmmTagger(transitions, emissions, tagDictWithEnds, startEndSymbol, startEndTag)
  }

}

/**
 * Factory for training a Hidden Markov Model tagger from a combination of
 * labeled data and unlabeled data using the Expectation-Maximization (EM)
 * algorithm.
 *
 * @tparam Sym	visible symbols in the sequences
 * @tparam Tag	tags applied to symbols
 *
 * @param estimatedTransitionCountsTransformer		factory for generating builders that count tag occurrences and compute distributions during EM
 * @param estimatedEmissionCountsTransformer		factory for generating builders that count symbol occurrences and compute distributions during EM
 * @param startEndSymbol						a unique start/end symbol used internally to mark the beginning and end of a sentence
 * @param startEndTag							a unique start/end tag used internally to mark the beginning and end of a sentence
 * @param maxIterations							maximum number of iterations to be run during EM
 * @param minAvgLogProbChangeForEM				stop iterating EM if change in average log probability is less than this threshold
 */
trait AbstractEmHmmTaggerTrainer[Sym, Tag] {
  protected val estimatedTransitionCountsTransformer: CondCountsTransformer[Tag, Tag]
  protected val estimatedEmissionCountsTransformer: CondCountsTransformer[Tag, Sym]
  protected val startEndSymbol: Sym
  protected val startEndTag: Tag
  protected val maxIterations: Int = 50
  protected val minAvgLogProbChangeForEM: Double = 0.00001

  protected val LOG = LogFactory.getLog(classOf[AbstractEmHmmTaggerTrainer[Sym, Tag]])

  /**
   * Re-estimate probability distributions using EM.  Estimate counts for
   * each sequence in rawTrainSequences using the forward/backward procedure.
   * Calculate probability distributions from these counts.  Repeat until
   * convergence.
   */
  protected def reestimateProbabilityDistributions(
    tagDict: Map[Sym, Set[Tag]],
    rawTrainSequences: Iterable[IndexedSeq[Sym]],
    initialTransitionCounts: CondFreqCounts[Tag, Tag, Double],
    initialEmissionCounts: CondFreqCounts[Tag, Sym, Double],
    initialTransitions: Tag => Tag => Probability,
    initialEmissions: Tag => Sym => Probability) = {

    if (LOG.isDebugEnabled) {
      initialEmissions match {
        case ie: Map[String, Map[String, Probability]] =>
          if (ie contains "N") {
            LOG.debug("    initialEmissions(N)(default) = " + initialEmissions("N".asInstanceOf[Tag])("unknown word".asInstanceOf[Sym]).underlying)
            LOG.debug("                       (man)     = " + initialEmissions("N".asInstanceOf[Tag])("man".asInstanceOf[Sym]).underlying)
            LOG.debug("    initialEmissions(I)(default) = " + initialEmissions("I".asInstanceOf[Tag])("unknown word".asInstanceOf[Sym]).underlying)
            LOG.debug("                       (man)     = " + initialEmissions("I".asInstanceOf[Tag])("man".asInstanceOf[Sym]).underlying)
          }
          else {
            LOG.debug("    initialEmissions(NN)(default) = " + initialEmissions("NN".asInstanceOf[Tag])("unknown word".asInstanceOf[Sym]).underlying)
            LOG.debug("                        (man)     = " + initialEmissions("NN".asInstanceOf[Tag])("man".asInstanceOf[Sym]).underlying)
            LOG.debug("    initialEmissions(IN)(default) = " + initialEmissions("IN".asInstanceOf[Tag])("unknown word".asInstanceOf[Sym]).underlying)
            LOG.debug("                        (man)     = " + initialEmissions("IN".asInstanceOf[Tag])("man".asInstanceOf[Sym]).underlying)
          }
        case _ =>
          LOG.debug("    Empty FreqDist")
      }
    }

    // initial transition and emission probability distributions to be 
    // re-estimated using EM.  
    var transitions = initialTransitions
    var emissions = initialEmissions

    var prevAvgLogProb = Double.NegativeInfinity
    var averageLogProb = Double.NegativeInfinity
    var iteration = 0
    do {
      // update iteration information
      iteration += 1
      prevAvgLogProb = averageLogProb

      // E Step:  Use the forward/backward procedure to determine the 
      //          probability of various possible state sequences for 
      //          generating the training data

      val (expectedTransitionCounts, expectedEmmissionCounts, avgLogProb) =
        estimateCounts(rawTrainSequences, tagDict, transitions, emissions, initialTransitionCounts, initialEmissionCounts)

      // M Step: Use these probability estimates to re-estimate the 
      //         probability distributions

      transitions = CondFreqDist(estimatedTransitionCountsTransformer(expectedTransitionCounts))
      emissions = CondFreqDist(estimatedEmissionCountsTransformer(expectedEmmissionCounts))

      // compute new iteration information
      averageLogProb = avgLogProb
      LOG.info("\t" + iteration + ": " + averageLogProb)

    } while (iteration < maxIterations &&
      (averageLogProb - prevAvgLogProb).abs > minAvgLogProbChangeForEM && //check if converged
      averageLogProb > prevAvgLogProb) // check for divergence

    if ((averageLogProb - prevAvgLogProb).abs < minAvgLogProbChangeForEM)
      LOG.info("DONE: Change in average log probability is less than " + minAvgLogProbChangeForEM)
    if (averageLogProb < prevAvgLogProb)
      LOG.info("DIVERGED: log probability decreased on iteration %d".format(iteration))
    if (averageLogProb == Double.NegativeInfinity)
      throw new RuntimeException("averageLogProb == -Infinity on iteration %d".format(iteration))

    (transitions, emissions)
  }

  /**
   * Estimate transition and emission counts for each sequence in
   * rawTrainSequences using the forward/backward procedure.
   *
   * TODO: This method should be rewritten as a MapReduce job.
   */
  protected def estimateCounts(
    rawTrainSequences: Iterable[IndexedSeq[Sym]],
    tagDict: Map[Sym, Set[Tag]],
    transitions: Tag => Tag => Probability,
    emissions: Tag => Sym => Probability,
    initialTransitionCounts: CondFreqCounts[Tag, Tag, Double],
    initialEmissionCounts: CondFreqCounts[Tag, Sym, Double]) = {

    val (expectedTransitionCounts, expectedEmissionCounts, totalSeqProb, numSequences) =
      rawTrainSequences.par
        .map(sequence => estimateCountsForSequence(sequence, transitions, emissions, tagDict))
        .map {
          case (estTrCounts, estEmCounts, seqProb) => (estTrCounts, estEmCounts, seqProb.underlying, 1) // number of sentences == 1
        }
        .fold((CondFreqCounts[Tag, Tag, Double](), CondFreqCounts[Tag, Sym, Double](), 0., 0)) {
          case ((aTC, aEC, aP, aN), (bTC, bEC, bP, bN)) =>
            (aTC ++ bTC, aEC ++ bEC, aP + bP, aN + bN) // sum up all the components
        }

    (expectedTransitionCounts.toMap, expectedEmissionCounts.toMap, totalSeqProb / numSequences)
  }

  /**
   * Estimate transition and emission counts for the given sequence using
   * the forward/backward procedure.
   */
  protected def estimateCountsForSequence(
    sequence: IndexedSeq[Sym],
    transitions: Tag => Tag => Probability,
    emissions: Tag => Sym => Probability,
    tagDict: Map[Sym, Set[Tag]]) = {

    val (forwards, forwardProb) = forwardProbabilities(sequence, transitions, emissions, tagDict)
    val (backwrds, backwrdProb) = backwrdProbabilities(sequence, transitions, emissions, tagDict)
    assert(forwardProb approx backwrdProb, "forward=%s, backward=%s".format(forwardProb.underlying, backwrdProb.underlying))
    val seqProb = forwardProb // P(sequence | transition,emissions)

    // Get expected transition counts based on forward-backward probabilities
    //        Let expectedTransitionCounts(t)(i)(j) be the probability of being in 
    //            state i at time t and state j at time t+1
    val expectedTransitionCounts = estimateTransitionCounts(sequence, transitions, emissions, tagDict, forwards, backwrds, seqProb)

    // Get expected emission counts based on forward-backward probabilities
    //        Let expectedEmissionCounts(t)(i)(j) be the probability of being in 
    //            state i at time t given the observations and the model
    val expectedEmissionCounts = estimateEmissionCounts(sequence, tagDict, forwards, backwrds, seqProb)

    (expectedTransitionCounts, expectedEmissionCounts, seqProb)
  }

  /**
   * Calculate forward probabilities for the sequence based on the existing
   * transition and emission probabilities.
   *
   * Let forward(t)(j) be the probability of being in state j after seeing the
   *     first t observations (by summing over all initial paths leading to j).
   *
   *             forward(t)(j) = P(o1,o2,...,ot, q_t=j | lambda)
   */
  protected def forwardProbabilities(
    sequence: IndexedSeq[Sym],
    transitions: Tag => Tag => Probability,
    emissions: Tag => Sym => Probability,
    tagDict: Map[Sym, Set[Tag]]): (IndexedSeq[Tag => Probability], Probability) = {

    // Initialization
    //     forward(1)(j) = a(start)(j) * b(j)(o1)   j in [1,N]
    // Recursion
    //     forward(t)(j) = (1 to N).sum(i => forward(t-1)(i) * a(i)(j)) * bj(ot)    j in [1,N], t in [1,T]
    // Termination
    //     P(O | lambda) = forward(final)(sf) = (1 to N).sum(i => forward(T)(i) * aif)

    val startForward = Map(startEndTag -> Probability.one)

    val (lastForward @ Pattern.Map(`startEndTag` -> forwardProb), forwards) =
      (sequence :+ startEndSymbol).foldLeft((startForward, List[Map[Tag, Probability]]())) {
        case ((prevForward, otherForwards), tok) =>
          val currForward =
            tagDict(tok).mapTo { currTag => // each legal tag for the current token
              val tProb =
                prevForward.sumMap {
                  case (prevTag, prevFwdScore) => prevFwdScore * transitions(prevTag)(currTag)
                }
              val eProb = emissions(currTag)(tok)
              tProb * eProb
            }.toMap
          (currForward, prevForward :: otherForwards)
      }

    ((lastForward :: forwards).reverse.toIndexedSeq, forwardProb)
  }

  /**
   * Calculate backward probabilities for the sequence based on the existing
   * transition and emission probabilities.
   *
   * Let backwrd(t)(j) be the probability of observing the final set of observations
   *     from time t+1 to T given that one is in state i at time t
   *
   *             backwrd(j) = P(o1,o2,...,ot, q_t=j | lambda)
   */
  protected def backwrdProbabilities(
    sequence: IndexedSeq[Sym],
    transitions: Tag => Tag => Probability,
    emissions: Tag => Sym => Probability,
    tagDict: Map[Sym, Set[Tag]]): (IndexedSeq[Tag => Probability], Probability) = {

    // Initialization
    //     backwrd(T)(i) = a(i)(F)   i in [1,N]
    // Recursion
    //     backwrd(t)(i) = (1 to N).sum(j => a(i)(j) * b(j)(o_(t+1)) * backwrd(t+1)(j))    i in [1,N], t in [1,T]
    // Termination
    //     P(O | lambda) = backwrd(1)(s0) = (1 to N).sum(i => a(0)(j) * b(j)(o1) * backwrd(1)(j))

    val finalBackwrd = Map(startEndTag -> Probability.one)

    val (firstBackwrd @ Pattern.Map(`startEndTag` -> backwrdProb), backwrds, lastTok) =
      (startEndSymbol +: sequence).foldRight((finalBackwrd, List[Map[Tag, Probability]](), startEndSymbol)) {
        case (tok, (nextBackwrd, otherBackwrds, nextTok)) =>
          val currBackwrd =
            tagDict(tok).mapTo { currTag =>
              nextBackwrd.sumMap {
                case (nextTag, nextBkwdScore) =>
                  transitions(currTag)(nextTag) * emissions(nextTag)(nextTok) * nextBkwdScore
              }
            }.toMap
          (currBackwrd, nextBackwrd :: otherBackwrds, tok)
      }

    ((firstBackwrd :: backwrds).toIndexedSeq, backwrdProb)
  }

  /**
   * Estimate transition counts for the sequence based on forward and
   * backward probabilities.
   *
   *    estTrans(i,j) = sum_t(fwd(t)(i) * a(i)(j) * b(o(t+1)) * bkw(t+1)(j) / seqProb)
   */
  protected def estimateTransitionCounts(
    sequence: IndexedSeq[Sym],
    transitions: Tag => Tag => Probability,
    emissions: Tag => Sym => Probability,
    tagDict: Map[Sym, Set[Tag]],
    forwards: IndexedSeq[Tag => Probability],
    backwrds: IndexedSeq[Tag => Probability],
    seqProb: Probability) = {

    val validTagsByToken = sequence.map(tagDict)

    val nextTokens = sequence :+ startEndSymbol
    val currTagSets = Set(startEndTag) +: validTagsByToken
    val nextTagSets = validTagsByToken :+ Set(startEndTag)
    val currForwards = forwards.dropRight(1)
    val nextBackwrds = backwrds.drop(1)

    val expectedTransitionCounts =
      (nextTokens zipEqual currTagSets zipEqual nextTagSets zipEqual currForwards zipEqual nextBackwrds).map {
        case ((((nextTok, currTags), nextTags), currForward), nextBackwrd) =>
          currTags.mapTo { currTag =>
            nextTags.mapTo { nextTag =>
              (currForward(currTag) * transitions(currTag)(nextTag) * emissions(nextTag)(nextTok) * nextBackwrd(nextTag) / seqProb).toDouble
            }.toMap
          }.toMap
      }

    expectedTransitionCounts.map(CondFreqCounts(_)).reduce(_ ++ _)
  }

  /**
   * Estimate emission counts for the sequence based on forward and
   * backward probabilities.
   *
   *   estEmiss(t)(j) = fwd(t)(j) * bkw(t)(j) / seqProb
   */
  protected def estimateEmissionCounts(
    sequence: IndexedSeq[Sym],
    tagDict: Map[Sym, Set[Tag]],
    forwards: IndexedSeq[Tag => Probability],
    backwrds: IndexedSeq[Tag => Probability],
    seqProb: Probability) = {

    // TODO: Probably not necessary to count start/end states since it's 
    //       always the case that P(endSym|endTag)=1
    val fullSeq = startEndSymbol +: sequence :+ startEndSymbol

    val expectedEmissionCounts =
      (fullSeq zipEqual forwards zipEqual backwrds).map {
        case ((tok, forward), backwrd) =>
          tagDict(tok).mapTo(tag =>
            Map(tok -> (forward(tag) * backwrd(tag) / seqProb).toDouble)).toMap
      }

    expectedEmissionCounts.map(CondFreqCounts(_)).reduce(_ ++ _)
  }
}


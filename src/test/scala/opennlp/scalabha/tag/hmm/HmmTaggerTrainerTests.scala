package opennlp.scalabha.tag.hmm

import org.junit.Assert._
import org.junit._
import opennlp.scalabha.util.CollectionUtils._
import scala.io.Source
import opennlp.scalabha.tag._
import opennlp.scalabha.tag.support._
import opennlp.scalabha.tag.hmm.support._
import opennlp.scalabha.tag.TaggerEvaluator
import opennlp.scalabha.tag.ScoreResults
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.apache.log4j.PropertyConfigurator
import org.apache.log4j.BasicConfigurator

class HmmTaggerTrainerTests {

  @Test
  def unsupervised_training_ic_fullTagDict_noSmoothing() {
    val trainLab = TaggedFile("data/postag/ic/ictrain.txt")
    val testLab = TaggedFile("data/postag/ic/ictest.txt")

    val testRaw = AsRawFile("data/postag/ic/ictest.txt")
    val gold = TaggedFile("data/postag/ic/ictest.txt")

    val unsupervisedTrainer: UnsupervisedTaggerTrainer[String, String] =
      new HmmTaggerTrainer(
        initialTransitionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new RandomCondFreqCounter(5, new SimpleCondFreqCounter()) },
        initialEmissionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new RandomCondFreqCounter(5, new SimpleCondFreqCounter()) },
        estimatedTransitionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new SimpleCondFreqCounter() },
        estimatedEmissionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new SimpleCondFreqCounter() },
        "<END>", "<END>",
        maxIterations = 20,
        minAvgLogProbChangeForEM = 0.00001,
        new SimpleTagDictFactory())
    val tagDict = new SimpleTagDictFactory().make(trainLab ++ testLab)
    val unsupervisedTagger = unsupervisedTrainer.trainUnsupervised(tagDict, trainLab.map(_.map(_._1)))
    val output = unsupervisedTagger.tag(testRaw)
    val results = new TaggerEvaluator().evaluate(output, gold, unsupervisedTagger.asInstanceOf[HmmTagger[String, String]].tagDict)
    assertResultsEqual("""
		Total:   12.12 (4/33)
		Known:   12.12 (4/33)
		Unknown: NaN (0/0)
		Common Mistakes:
		#Err     Gold      Model
		15       H        C
		14       C        H
    	""", results)
  }

  @Test
  def unsupervised_training_en_fullTagDict_noSmoothing() {
    val trainLab = TaggedFile("data/postag/english/entrain")
    val testLab = TaggedFile("data/postag/english/entest")

    val testRaw = AsRawFile("data/postag/english/entest")
    val gold = TaggedFile("data/postag/english/entest")

    val unsupervisedTrainer: UnsupervisedTaggerTrainer[String, String] =
      new HmmTaggerTrainer(
        initialTransitionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new SimpleCondFreqCounter() },
        initialEmissionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new SimpleCondFreqCounter() },
        estimatedTransitionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new SimpleCondFreqCounter() },
        estimatedEmissionCounterFactory =
          new CondFreqCounterFactory[String, String] { def get() = new SimpleCondFreqCounter() },
        "<END>", "<END>",
        maxIterations = 20,
        minAvgLogProbChangeForEM = 0.00001,
        new SimpleTagDictFactory())
    val tagDict = new SimpleTagDictFactory().make(trainLab ++ testLab)
    val unsupervisedTagger = unsupervisedTrainer.trainUnsupervised(tagDict, trainLab.map(_.map(_._1)))
    val output = unsupervisedTagger.tag(testRaw)
    val results = new TaggerEvaluator().evaluate(output, gold, unsupervisedTagger.asInstanceOf[HmmTagger[String, String]].tagDict)
    assertResultsEqual("""
		Total:   88.42 (21176/23949)
		Known:   88.42 (21176/23949)
		Unknown: NaN (0/0)
		Common Mistakes:
		#Err     Gold      Model
		693      D        N
		522      D        F
		279      V        N
		198      J        N
		137      R        J
    	""", results)
  }

  @Test
  def unsupervised_training_en_testInTagDict() {
    val trainLab = TaggedFile("data/postag/english/entrain") ++ TaggedFile("data/postag/english/entest")
    val results = runUnsupervisedTrainingTest(trainLab)
    assertResultsEqual("""
		Total:   86.02 (20600/23949)
		Known:   86.02 (20600/23949)
		Unknown: NaN (0/0)
		Common Mistakes:
		#Err     Gold      Model
		972      D        N
		522      D        F
		378      V        N
		264      J        N
		176      R        J
    	""", results)
  }

  @Test
  def unsupervised_training_en_largeTagDict() {
    val trainLab = TaggedFile("data/postag/english/entrain")
    val results = runUnsupervisedTrainingTest(trainLab)
    assertResultsEqual("""
		Total:   77.46 (18550/23949)
		Known:   84.91 (18545/21841)
		Unknown: 0.24 (5/2108)
		Common Mistakes:
		#Err     Gold      Model
		1173     N        E
		968      D        N
		522      D        F
		341      V        N
		327      V        E
    	""", results)
  }

  private def runUnsupervisedTrainingTest(trainLab: Seq[IndexedSeq[(String, String)]]) = {
    val trainRaw = RawFile("data/postag/english/enraw20k")
    val testRaw = AsRawFile("data/postag/english/entest")
    val gold = TaggedFile("data/postag/english/entest")

    val tagDict = new SimpleTagDictFactory().make(trainLab)
    val allTags = tagDict.values.flatten.toSet + "<END>"

    val unsupervisedTrainer: UnsupervisedTaggerTrainer[String, String] =
      new HmmTaggerTrainer(
        initialTransitionCounterFactory =
          new CondFreqCounterFactory[String, String] {
            def get() =
              new SimpleSmoothingTransitionFreqCounter(1.0, "<END>",
                FreqCounts(),
                Map(), //val backoffTransitionCounts = CondFreqCounts(for (a <- allTags; b <- allTags) yield (a, b))
                new SimpleCondFreqCounter())
          },
        initialEmissionCounterFactory =
          new CondFreqCounterFactory[String, String] {
            def get() =
              new SimpleSmoothingEmissionFreqCounter(1.0, "<END>", "<END>",
                FreqCounts(trainRaw.flatten.counts),
                tagDict.flattenOver.map(_.swap).groupByKey.mapValuesStrict(_.counts.count(_._2 == 1)),
                new SimpleCondFreqCounter[String, String]())
          },
        estimatedTransitionCounterFactory =
          new CondFreqCounterFactory[String, String] {
            def get() =
              new SimpleCondFreqCounter()
          },
        estimatedEmissionCounterFactory =
          new CondFreqCounterFactory[String, String] {
            def get() =
              new SimpleCondFreqCounter[String, String]()
          },
        "<END>", "<END>",
        maxIterations = 20,
        minAvgLogProbChangeForEM = 0.00001,
        null)
    val unsupervisedTagger = unsupervisedTrainer.trainUnsupervised(tagDict, trainRaw)
    val output = unsupervisedTagger.tag(testRaw)
    val results = new TaggerEvaluator().evaluate(output, gold, unsupervisedTagger.asInstanceOf[HmmTagger[String, String]].tagDict)
    results
  }

  object TaggedFile {
    def apply(filename: String) =
      Source.fromFile(filename).getLines
        .map(_.trim)
        .split("###/###")
        .filter(_.nonEmpty)
        .map(_.map(_.split("/").toSeq.toTuple2).toIndexedSeq)
  }

  object AsRawFile {
    def apply(filename: String) =
      TaggedFile(filename).map(_.map(_._1))
  }

  object RawFile {
    def apply(filename: String) =
      Source.fromFile(filename).getLines
        .map(_.trim)
        .split("###")
        .filter(_.nonEmpty)
        .map(_.toIndexedSeq)
  }

  def assertResultsEqual(expectedString: String, results: ScoreResults[String, String]) {
    assertEquals(
      expectedString.split("\n").map(_.trim).filter(_.nonEmpty).mkString("\n"),
      results.toString.split("\n").map(_.trim).filter(_.nonEmpty).mkString("\n"))
  }
}

object HmmTaggerTrainerTests {

  @BeforeClass def turnOffLogging() {
    Logger.getRootLogger.setLevel(Level.DEBUG)
  }

}

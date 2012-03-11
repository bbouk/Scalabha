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

class SemisupervisedHmmTaggerTrainerTests {

  @Test
  def en_comparisonA() {
    val (tagDictTrain, labeledTrain) = TaggedFile("data/postag/english/entrain").splitAt(3000)
    val tagDict = new SimpleTagDictFactory().make(tagDictTrain)
    val results = runUnsupervisedTrainingTest(tagDict, tagDictTrain)
    assertResultsEqual("""
		Total:   91.15 (21829/23949)
		Known:   96.63 (20745/21469)
		Unknown: 43.71 (1084/2480)
		Common Mistakes:
		#Err     Gold      Model
		195      N        J
		167      V        N
		159      N        V
		136      N        D
		107      J        N
    	""", results)
  }

  private def runUnsupervisedTrainingTest(tagDict: Map[String, Set[String]], trainLab: Seq[IndexedSeq[(String, String)]]) = {
    val trainRaw = RawFile("data/postag/english/enraw20k")
    val gold = TaggedFile("data/postag/english/entest")

    println("tagDictTrain.size = " + tagDict.flattenOver.size)
    println("labeledTrain.size = " + trainLab.size)
    println("rawTrain.size     = " + trainRaw.size)

    val trainer: SemisupervisedTaggerTrainer[String, String] =
      new SemisupervisedHmmTaggerTrainer(
        initialTransitionCounterFactory = new CondFreqCounterFactory[String, String] {
          def get() =
            new EisnerSmoothingCondFreqCounter[String, String](lambda = 1.0,
              new FreqCounterFactory[String] { def get() = new ItemDroppingFreqCounter("<END>", new SimpleFreqCounter[String]()) },
              new SimpleCondFreqCounter())
        },
        initialEmissionCounterFactory = new CondFreqCounterFactory[String, String] {
          def get() =
            new StartEndFixingEmissionFreqCounter[String, String]("<END>", "<END>",
              new EisnerSmoothingCondFreqCounter(lambda = 1.0,
                new FreqCounterFactory[String] { def get() = new AddLambdaSmoothingFreqCounter(lambda = 1.0, new SimpleFreqCounter()) },
                new StartEndFixingEmissionFreqCounter[String, String]("<END>", "<END>",
                  new SimpleCondFreqCounter())))
        },
        estimatedTransitionCounterFactory = new CondFreqCounterFactory[String, String] {
          def get() =
            new EisnerSmoothingCondFreqCounter[String, String](lambda = 1.0,
              new FreqCounterFactory[String] { def get() = new ItemDroppingFreqCounter("<END>", new SimpleFreqCounter[String]()) },
              new SimpleCondFreqCounter())
        },
        estimatedEmissionCounterFactory = new CondFreqCounterFactory[String, String] {
          def get() =
            new StartEndFixingEmissionFreqCounter[String, String]("<END>", "<END>",
              new EisnerSmoothingCondFreqCounter(lambda = 1.0,
                new FreqCounterFactory[String] { def get() = new AddLambdaSmoothingFreqCounter(lambda = 1.0, new SimpleFreqCounter()) },
                new StartEndFixingEmissionFreqCounter[String, String]("<END>", "<END>",
                  new SimpleCondFreqCounter())))
        },
        "<END>", "<END>",
        maxIterations = 20,
        minAvgLogProbChangeForEM = 0.00001)
    val tagger = trainer.trainSemisupervised(tagDict, trainRaw, trainLab)
    val output = tagger.tag(gold.map(_.map(_._1)))
    val results = new TaggerEvaluator().evaluate(output, gold, tagDict)
    results
  }

  object TaggedFile {
    def apply(filename: String): List[IndexedSeq[(String, String)]] =
      Source.fromFile(filename).getLines
        .map(_.trim)
        .split("###/###")
        .filter(_.nonEmpty)
        .map(_.map(_.split("/").toSeq.toTuple2).toIndexedSeq)
  }

  object AsRawFile {
    def apply(filename: String): List[IndexedSeq[String]] =
      TaggedFile(filename).map(_.map(_._1))
  }

  object RawFile {
    def apply(filename: String): List[IndexedSeq[String]] =
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

object SemisupervisedHmmTaggerTrainerTests {

  @BeforeClass def turnOffLogging() {
    Logger.getRootLogger.setLevel(Level.OFF)
  }

}

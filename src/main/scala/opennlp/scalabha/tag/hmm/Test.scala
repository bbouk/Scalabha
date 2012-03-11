package opennlp.scalabha.tag.hmm

import opennlp.scalabha.tag._
import opennlp.scalabha.tag.support._
import opennlp.scalabha.tag.hmm.support._

object Test {

  def main(args: Array[String]): Unit = {
    val tagDictFile = TaggedFile("data/s00-15.pos").toList
    val unlabTrainFile = AsRawFile("data/s16-18.pos").toList
    val labeledTest = TaggedFile("data/s19-21.pos").toList

    val tagDict = new SimpleTagDictFactory().make(tagDictFile)
    for (
      (name, unsuperEmissDist) <- List(
        "OneCountUnsupervisedEmissionDistFactory" -> new OneCountUnsupervisedEmissionDistFactory(tagDict, lambda = 1.0, "<END>", "<END>"),
        "PartialCountUnsupervisedEmissionDistFactory" -> new PartialCountUnsupervisedEmissionDistFactory(tagDict, lambda = 1.0, "<END>", "<END>"),
        "EstimatedRawCountUnsupervisedEmissionDistFactory" -> new EstimatedRawCountUnsupervisedEmissionDistFactory(tagDict, unlabTrainFile, lambda = 1.0, "<END>", "<END>"),
        "Hacked OneCountUnsupervisedEmissionDistFactory" -> new DefaultHackingUnsupervisedEmissionDistFactory(tagDict, "<END>", "<END>", new OneCountUnsupervisedEmissionDistFactory(tagDict, lambda = 1.0, "<END>", "<END>")),
        "Hacked PartialCountUnsupervisedEmissionDistFactory" -> new DefaultHackingUnsupervisedEmissionDistFactory(tagDict, "<END>", "<END>", new PartialCountUnsupervisedEmissionDistFactory(tagDict, lambda = 1.0, "<END>", "<END>")),
        "Hacked EstimatedRawCountUnsupervisedEmissionDistFactory" -> new DefaultHackingUnsupervisedEmissionDistFactory(tagDict, "<END>", "<END>", new EstimatedRawCountUnsupervisedEmissionDistFactory(tagDict, unlabTrainFile, lambda = 1.0, "<END>", "<END>")))) {
      println("EXPERIMENT: " + name)
      val unsupervisedTrainer: UnsupervisedTaggerTrainer[String, String] =
        new UnsupervisedHmmTaggerTrainer(
          initialUnsupervisedEmissionDist =
            unsuperEmissDist.make(),
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
      val unsupervisedTagger = unsupervisedTrainer.trainUnsupervised(tagDict, unlabTrainFile)
      val output = unsupervisedTagger.tag(labeledTest.map(_.map(_._1)))
      val results = new TaggerEvaluator().evaluate(output, labeledTest, tagDict)
      println(results)
    }
  }

  case class TaggedFile(filename: String) extends Iterable[IndexedSeq[(String, String)]] {
    def iterator =
      io.Source.fromFile(filename).getLines
        .map(_.trim
          .split(" ")
          .map(_.split("\\|").toSeq match { case Seq(w, t) => (w, t); case x => throw new RuntimeException(x.toString) }).toIndexedSeq)
  }

  case class AsRawFile(filename: String) extends Iterable[IndexedSeq[String]] {
    def iterator =
      TaggedFile(filename).iterator.map(_.map(_._1))
  }

}

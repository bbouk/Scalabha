package opennlp.scalabha.tag.hmm.support

import opennlp.scalabha.util.CollectionUtils._
import opennlp.scalabha.util.LogNum
import opennlp.scalabha.tag.support._
import org.apache.commons.logging.LogFactory
import org.junit.BeforeClass
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.junit.Test
import org.junit.Assert._
import opennlp.scalabha.tag.SimpleTagDict

class UnsupervisedEmissionDistTests {

  @Test
  def test_EstimatedRawCountUnsupervisedEmissionDistFactory() {

    val rawData = List(
      "the dog walks quickly",
      "the cat walks quietly",
      "the dog saw the cat",
      "the cat saw the dog",
      "the dog saw the saw",
      "the bird sings",
      "the mouse walks",
      "the aardvark walks",
      "the aardvark meanders").map(_.split(" ").toList)

    val tagDict = SimpleTagDict(Map(
      "bird" -> Set('N),
      "cat" -> Set('N),
      "dog" -> Set('N),
      "horse" -> Set('N),
      "mouse" -> Set('N),
      "quickly" -> Set('R),
      "quietly" -> Set('R),
      "saw" -> Set('N, 'V),
      "sings" -> Set('V),
      "the" -> Set('D),
      "walks" -> Set('V)))

    val d =
      new EstimatedRawCountUnsupervisedEmissionDistFactory[Symbol, String](
        new PassthroughCountsTransformer(),
        tagDict,
        rawData).make()

    for (w <- List("aardvark", "meanders", "horse", "unseen word", "dog", "the").map(Option(_)))
      for (t <- List('N, 'V, 'R, 'D).map(Option(_)))
        println("p(%s|%s) = %s".format(w, t, d(t)(w).logValue))

    println
    println(d(Some('N))(Some("aardvark")) / d(Some('V))(Some("aardvark")))
    println(d(Some('N))(Some("meanders")) / d(Some('V))(Some("meanders")))

    assertEqualsProb(LogNum(1. / 5.), d(Some('N))(Some("bird")))
  }

  def assertEqualsProb(a: LogNum, b: LogNum) {
    assertEquals(a.toDouble, b.toDouble, 0.001)
  }

  def assertEqualsDouble(a: Double, b: Double) {
    assertEquals(a.toDouble, b.toDouble, 0.0000001)
  }

}

object UnsupervisedEmissionDistTests {

  @BeforeClass def turnOffLogging() {
    Logger.getRootLogger.setLevel(Level.OFF)
  }

}

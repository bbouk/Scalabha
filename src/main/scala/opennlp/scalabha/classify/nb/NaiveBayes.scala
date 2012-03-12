package opennlp.scalabha.classify.nb

import scala.collection.mutable
import opennlp.scalabha.classify._
import opennlp.scalabha.util._
import opennlp.scalabha.tag.support._

/**
 * The naive Bayes classifier as a trait: defining common functionality that
 * are shared by different implementations of the NB classifier
 */
trait NaiveBayesClassifier[L,T] extends Classifier[L,T] {
  private val one = Probability(1.)

  def priorProb(label:L):Probability

  def featureProb(feature:T, label:L):Probability

  override def classify(document:Document[T]) =
    labels.map(label => {
      val prior = priorProb(label)
      val features = document.allFeatures
      val featuresProb = (one/:features.map(featureProb(_,label)))(_*_)
      (label, prior * featuresProb)
    })
}

/**
 * Implementation of the naive Bayes classifier which can be updated
 * online with new training examples; this is not a mutable structure
 * per se, but can create an updated classifier given a new training
 * example
 * @param labelDocCount the number of documents encoutered for each label L
 * @param labelFeatureCount the frequency of feature T given label L
 */
class OnlineNaiveBayesClassifier[L,T](
    val labelDocCounter:FreqCounter[L],
    val labelFeatureCounter:CondFreqCounter[L,T],
    val labels:mutable.Set[L])
extends NaiveBayesClassifier[L,T] {

  override def priorProb(label:L) = labelDocCounter.toFreqDist()(label)

  override def featureProb(feature:T, label:L) =
    (labelFeatureCounter.toFreqDist)(label)(feature)

  def update(label:L, document:Document[T]) = {
    labelDocCounter.increment(label, 1)
    document.allFeatures.foreach(labelFeatureCounter.increment(label, _, 1))
    labels += label
  }
}

/**
 * Utilities for OnlineNaiveBayesClassifier
 */
object OnlineNaiveBayesClassifier {

  /**
   * Creates a new online naive Bayes classifier with 0s for counts for
   * features and documents
   * @param lambda smoothing parameter for add-lambda smoothing
   */
  def empty[L,T](lambda:Double) = {
    val docCounter = new SimpleFreqCounter[L]
    val featureCounter =
      if (lambda == 0)
        new SimpleCondFreqCounter[L,T]()
      else
        new AddLambdaSmoothingCondFreqCounter(lambda,
                                              new SimpleCondFreqCounter[L,T])
    val labels = mutable.Set.empty[L]
    new OnlineNaiveBayesClassifier(docCounter, featureCounter, labels)
  }
}

/**
 * A classifier trainer for an online naive Bayes classifier
 * @param lambda a smoothing parameter for add-lambda smoothing
 */
class OnlineNaiveBayesClassifierTrainer[L,T](val lambda:Double) 
extends ClassifierTrainer[L,T] {
  def train(labeledDocuments:Iterator[(L,Document[T])]) = {
    val current = OnlineNaiveBayesClassifier.empty[L,T](lambda)
    // ld = (label, document)
    labeledDocuments.foreach(ld => current.update(ld._1, ld._2))
    current
  }
}

object OnlineNaiveBayesClassifierTrainer {
  def apply[L,T](n:Int) = new OnlineNaiveBayesClassifierTrainer[L,T](n)
}

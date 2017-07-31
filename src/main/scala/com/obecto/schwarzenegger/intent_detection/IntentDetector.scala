package com.obecto.schwarzenegger.intent_detection

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by gbarn_000 on 6/16/2017.
  */
abstract class IntentDetector extends Actor {

  import context.dispatcher

  implicit val system = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))
  val http = Http(system)
  var intentCache: Option[IntentCache] = None
  var currentSender: ActorRef = _

  def detectIntent(message: String): Future[IntentData]

  def extractIntentData(json: String): IntentData

  def resetContext(context: Option[String] = None)

  def receive = {
    case message: String =>
      currentSender = sender()
      extractCachedIntentData match {

        case Some(intentData) =>
          sendIntentDataToSender(intentData)

        case None =>
          detectIntent(message).onComplete {
            case Success(intentData: IntentData) =>
              onResponseIntentDetectionBehaviour(message, intentData)
            case Failure(failure) =>
              failureIntentDetectionBehaviour(message, failure)
          }
      }

    case ClearIntentCache =>
      intentCache = None
  }

  private def sendIntentDataToSender(intentData: IntentData): Unit = {
    currentSender ! intentData
  }

  private def extractCachedIntentData: Option[IntentData] = {
    intentCache match {
      case Some(cache) =>
        Some(IntentData(cache.intentData.intent, cache.intentData.params))
      case None =>
        None
    }
  }

  private def failureIntentDetectionBehaviour(message: String, failure: Throwable): Unit = {
    println("Unable to detect intent... " + failure.getMessage)
    val emptyIntentData = IntentData(IntentDetector.INTENT_UNKNOWN, Map())
    onResponseIntentDetectionBehaviour(message, emptyIntentData)
  }

  private def onResponseIntentDetectionBehaviour(message: String, intentData: IntentData): Unit = {
    cacheIntent(message, intentData)
    sendIntentDataToSender(intentData)
  }

  private def cacheIntent(message: String, intentData: IntentData): IntentCache = {
    val newIntentCache = IntentCache(message, intentData)
    intentCache = Some(newIntentCache)
    newIntentCache
  }

}

object IntentDetector {
  final val INTENT_UNKNOWN = "unknown"

  def props(intentDetectorClass: Class[_ <: IntentDetector]): Props = Props.apply(intentDetectorClass)
}

case object ClearIntentCache

case object ResetContext

case class IntroduceIntentDetector(detector: ActorRef)

case class IntentData(intent: String, params: Map[String, String])

case class IntentCache(message: String, intentData: IntentData)
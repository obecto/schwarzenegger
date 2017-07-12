package com.obecto.schwarzenegger.intent_detection

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.obecto.schwarzenegger.messages.MessageInternal

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

  def detectIntent(message: MessageInternal): Future[IntentData]

  def extractIntentData(json: String): IntentData

  def resetContext(context: Option[String] = None)

  def receive = {
    case message: MessageInternal =>
      val currentSender = sender()
      intentCache match {
        case Some(cache) =>
          //println("The message is the same as the cached one so we return the cache!")
          currentSender ! IntentData(cache.intentData.intent, cache.intentData.params)
        case None =>
          val intentFuture: Future[IntentData] = detectIntent(message)
          intentFuture.onComplete {
            case Success(intentData: IntentData) =>
              intentCache = Some(IntentCache(message.text, intentData))
              currentSender ! intentData
            case Failure(failure) =>
              println("Unable to detect intent... " + failure.getMessage)
              val emptyIntentData = IntentData(IntentDetector.INTENT_UNKNOWN, Map())
              intentCache = Some(IntentCache(message.text, emptyIntentData))
              currentSender ! emptyIntentData
          }
      }
    case ClearIntentCache =>
      intentCache = None
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

case class IntentCache(text: String, intentData: IntentData)
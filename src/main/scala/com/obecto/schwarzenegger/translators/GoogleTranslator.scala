package com.obecto.schwarzenegger.translators

import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.HttpProtocols.`HTTP/1.0`
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.obecto.schwarzenegger.Keys
import spray.json._

/**
  * Created by gbarn_000 on 6/15/2017.
  */
class GoogleTranslator extends Translator with DefaultJsonProtocol {

  import system.dispatcher

  implicit val system = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  val http = Http(system)
  private val googleTranslateToken: String = Keys.GOOGLE_TRANSLATE_TOKEN
  if (googleTranslateToken.equals("")) {
    throw new IllegalArgumentException("No google translate token found.")
  }

  def receive = {

    case message: String =>
      translateText(message, sender())

  }

  override def translateText(text: String, sender: ActorRef) {
    val translateEntityString =
      raw"""{
              "q": "$text",
              "target": "en",
            }"""
    http.singleRequest(HttpRequest(
      POST,
      uri = "https://translation.googleapis.com/language/translate/v2?key=" + googleTranslateToken,
      entity = HttpEntity(`application/json`, translateEntityString),
      protocol = `HTTP/1.0`
    )).onComplete {
      (response) =>
        response.fold(
          failure => println(failure.getMessage),
          success => success.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach(body =>
            sender ! extractDataFromTranslate(body.utf8String)
          )
        )
    }
  }

  override def extractDataFromTranslate(message: String): String = {
    message.replace('[', ' ').replace(']', ' ')
      .parseJson.asJsObject().fields("data")
      .asJsObject().fields("translations")
      .asJsObject().fields("translatedText").convertTo[String]
  }
}

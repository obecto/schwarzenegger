package com.obecto.schwarzenegger.intent_detection

import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.HttpProtocols.`HTTP/1.1`
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, headers}
import akka.util.ByteString
import com.obecto.schwarzenegger.messages.HandleMessage
import com.obecto.schwarzenegger.{Config, Keys}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Ioan on 15-Jun-17.
  */
class GoogleIntentDetector extends IntentDetector with DefaultJsonProtocol {

  implicit val timeout = Config.REQUEST_TIMEOUT
  private val apiAiToken = Keys.API_AI_TOKEN
  if (apiAiToken.equals("")) {
    throw new NoSuchElementException("No API.AI token found.")
  }

  override def extractIntentData(intentData: String): IntentData = {
    var intent: String = ""
    var params: Map[String, String] = Map()

    // println("Trying to extract google intent data: " + intentData)
    val response = intentData.parseJson.asJsObject().fields("result").asJsObject()

    response.fields("metadata") match {
      case JsObject(entry) =>
        entry.get("intentName") match {
          case Some(intentValue) => intent = intentValue.convertTo[String]
          case None => ()
        }
      // println("Intent is " + intent)
      case _ =>
    }
    response.fields("parameters") match {
      case JsObject(entry) =>
        params = entry map {
          case (string, jsValue) =>
            string -> jsValue.convertTo[String]
        }
      //println("params are " + params)
      case _ =>
    }

    if (intent.equals("input.unknown") || intent.equals("")) {
      IntentData(IntentDetector.INTENT_UNKNOWN, params)
    } else {
      IntentData(intent, params)
    }
  }

  override def detectIntent(message: String): Future[IntentData] = {
    val authorization = headers.Authorization(OAuth2BearerToken(apiAiToken))
    val body =
      raw"""{
                      "query": [
                          "${message}"
                      ],
                      "lang": "en",
                      "sessionId": "${context.parent.path.name}"
                  }"""

    val topicResponseFuture = http.singleRequest(HttpRequest(
      POST,
      uri = "https://api.api.ai/v1/query?v=20150910",
      entity = HttpEntity(`application/json`, body),
      headers = List(authorization),
      protocol = `HTTP/1.1`
    ))

    topicResponseFuture.flatMap {
      response =>
        response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).flatMap(body => {
          Future(extractIntentData(body.utf8String))
        })
    }
  }

  override def resetContext(aiContext: Option[String]): Unit = aiContext match {
    //TODO Handle result of reset contest request
    case Some(text) =>
      val authorization = headers.Authorization(OAuth2BearerToken(apiAiToken))
      val uri = "https://api.api.ai/v1/contexts?sessionId=" + context.parent.path.name
      val body =
        raw"""{
              "name":"$text"
          }"""
      http.singleRequest(HttpRequest(
        POST,
        uri = uri,
        entity = HttpEntity(`application/json`, body),
        headers = List(authorization),
        protocol = `HTTP/1.1`
      ))
    case None =>
  }
}



package com.obecto.schwarzenegger.communicators

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.HttpProtocols.`HTTP/1.1`
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.{ByteString, Timeout}
import com.obecto.schwarzenegger.Keys
import com.obecto.schwarzenegger.messages.MessageReceived
import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsString}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by gbarn_000 on 6/15/2017.
  */
class MessangerCommunicator(server: Option[Future[ServerBinding]] = None, serverConfig: Option[DefaultServerConfig] = None) extends Communicator
  with Directives
  with SprayJsonSupport
  with DefaultJsonProtocol {

  import context.dispatcher

  implicit val timeout = Timeout(5.seconds)
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  http = Http(context.system)


  private var fbAccessToken: String = Keys.FB_ACCESS_TOKEN
  if (fbAccessToken.equals("")) {
    throw new IllegalArgumentException("Facebook access token is not set.")
  }
  private var fbVerificationToken: String = Keys.FB_VERIFY_TOKEN
  if (fbVerificationToken.equals("")) {
    throw new IllegalArgumentException("Facebook verification token is not set.")
  }

  server match {
    case Some(serverFuture) => println("Using external server... " + serverFuture)
    case None => startDefaultServer()
  }

  override def startDefaultServer(): Future[ServerBinding] = {
    implicit def myExceptionHandler: ExceptionHandler =
      ExceptionHandler {
        case _ => complete("EXCEPTION HANDLER")
      }

    val defaultServerConfig = serverConfig.get
    val defaultServer = http.bindAndHandle(routes, defaultServerConfig.interface, defaultServerConfig.port)
    println("Starting default server " + defaultServer.toString)
    defaultServer
  }

  override def sendTextResponse(text: String, senderId: String): Unit = {

    println("Sending message with text : " + text + " and senderId:  " + senderId)

    val uri: String = "https://graph.facebook.com/v2.6/me/messages?access_token=" + fbAccessToken
    val stringEntity: String =
      raw"""{
                                          "recipient" : {
                                              "id": "$senderId"
                                           },
                                           "message": {
                                              "text": "$text"
                                           }
                                     }"""
    println("URI IS : " + uri)
    println("stringEntity is : " + stringEntity)

    http.singleRequest(HttpRequest(
      POST,
      uri,
      entity = HttpEntity(`application/json`, stringEntity),
      protocol = `HTTP/1.1`
    )).onComplete {
      (response) =>
        response.fold(
          failure => println("OUR ERROR BODY IS : " + failure.getMessage),
          success => success.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            .foreach(body => println("OUR SUCCESS BODY IS : " + body.utf8String))
        )
    }
  }

  def routes: Route = {
    path("webhook") {
      get {
        parameters("hub.verify_token", "hub.challenge") {
          case (token, challenge) =>
            if (token.equals(fbVerificationToken)) {
              complete(challenge)
            } else {
              complete("Error, invalid token")
            }
        }
      } ~ post {
        entity(as[JsObject]) { data =>
          val fields = data.fields
          fields("object") match {
            case JsString("page") =>
              fields("entry") match {
                case JsArray(entry) => entry foreach {
                  messagingEvent =>
                    val id = messagingEvent.asJsObject.fields("id")
                    val time = messagingEvent.asJsObject.fields("time")
                    messagingEvent.asJsObject.fields("messaging") match {
                      case JsArray(messaging) => messaging foreach {
                        event =>
                          val eventFields = event.asJsObject.fields
                          val senderId = eventFields("sender").asJsObject.fields("id").convertTo[String]
                          val message = eventFields("message").asJsObject.fields("text").convertTo[String]

                          println("SenderId is : " + senderId + " And message is : " + message)
                          self ! MessageReceived(message, senderId)
                      }
                      case _ => complete("Not matched anything")
                    }
                }
                case _ => complete("Not matched anything")
              }
            case _ => complete("Not matched anything")
          }
          complete("Not matched anything")
        }
      }
    }
  }
}


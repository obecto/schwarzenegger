package com.obecto.schwarzenegger

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.obecto.schwarzenegger.communicators._
import com.obecto.schwarzenegger.example.{DateTopic, DefaultTopic, ExampleTopic}
import com.obecto.schwarzenegger.intent_detection.GoogleIntentDetector
import com.obecto.schwarzenegger.translators.{GoogleTranslator, Translator}
import com.typesafe.config.ConfigFactory

/**
  * Created by gbarn_000 on 6/12/2017.
  */
object Arnold extends App {
  implicit val system = ActorSystem("default")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val config = ConfigFactory.load()

  val fbVerifyToken: String = config.getString("fb.verify_token")
  val fbAccessToken: String = config.getString("fb.access_token")
  val googleApiAi: String = config.getString("google.apiai")
  val googleTranslateToken: String = config.getString("google.translate")
  val httpInterface = config.getString("http.interface")
  val httpPort = config.getInt("http.port")

  Keys.FB_ACCESS_TOKEN = fbAccessToken
  Keys.FB_VERIFY_TOKEN = fbVerifyToken
  Keys.GOOGLE_TRANSLATE_TOKEN = googleTranslateToken
  Keys.API_AI_TOKEN = googleApiAi

  //  val messangerCommunicator = system.actorOf(Communicator.props(classOf[MessangerCommunicator],
  //    DefaultServerConfig(httpInterface, httpPort)),"MessangerCommunicator")
  val consoleCommunicator = system.actorOf(Props[ConsoleCommunicator], "ConsoleCommunicator")
  val googleTranslator = system.actorOf(Translator.props(classOf[GoogleTranslator]), "GoogleTranslate")

  val googleIntentDetectorType = classOf[GoogleIntentDetector]
  val googleTranslatorType = classOf[GoogleTranslator]
  val list = List(
    TopicDescriptorType(classOf[ExampleTopic]),
    TopicDescriptorType(classOf[DateTopic]),
    TopicDescriptorType(classOf[DefaultTopic], isStatic = true)
  )

  val engine = system.actorOf(Engine.props(consoleCommunicator, googleTranslatorType, googleIntentDetectorType, list), "Engine")

}

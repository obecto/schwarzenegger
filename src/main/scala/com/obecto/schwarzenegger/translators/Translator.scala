package com.obecto.schwarzenegger.translators

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.HttpExt


/**
  * Created by gbarn_000 on 6/12/2017.
  */

trait Translator extends Actor {
  def http: HttpExt
  def translateText(text: String, sender: ActorRef)
  def extractDataFromTranslate(message: String): String
}

object Translator {

  def props(translatorClass: Class[_ <: Translator]): Props = Props.apply(translatorClass)

}



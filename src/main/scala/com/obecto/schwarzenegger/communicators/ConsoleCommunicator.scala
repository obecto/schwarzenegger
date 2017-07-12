package com.obecto.schwarzenegger.communicators

import com.obecto.schwarzenegger.messages.MessageExternal

import scala.concurrent.duration._

/**
  * Created by Ioan on 10-Jul-17.
  */
class ConsoleCommunicator() extends Communicator {

  import context.dispatcher

  println("Hello, please start a conversation!")
  context.system.scheduler.schedule(0 seconds, 3 seconds) {
    inputNext()
  }


  def inputNext(): Unit = {
    val text = scala.io.StdIn.readLine()
    self ! MessageExternal(text, "123")
  }

  override def sendInteractiveResponse(response: Object, senderId: String) = ???

  override def startDefaultServer() = ???

  override def sendTextResponse(text: String, senderId: String): Unit = {
    println(Console.BLUE + text + Console.RESET)
  }
}


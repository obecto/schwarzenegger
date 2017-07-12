package com.obecto.schwarzenegger

/**
  * Created by gbarn_000 on 7/5/2017.
  */
object messages {
  case class MessageExternal(text: String, senderId: String)
  case class MessageProcessed(text: String, senderId : String)
  case class MessageInternal(text:String)
}

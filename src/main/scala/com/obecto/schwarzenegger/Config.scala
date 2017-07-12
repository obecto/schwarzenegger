package com.obecto.schwarzenegger

import akka.util.Timeout
import scala.concurrent.duration._

/**
  * Created by gbarn_000 on 6/24/2017.
  */
object Config {

  final lazy val REQUEST_TIMEOUT = Timeout(5.second)

}

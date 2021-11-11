import zhttp.http._
import zhttp.service._
import zhttp.service.server.ServerChannelFactory
import zio._

import scala.util.Try

object HelloWorldWithContinue extends App {
  // Set a port
  private val PORT = 8090

  private val app = HttpApp.collectM {
    case Method.GET -> !! / "random"       => random.nextString(10).map(Response.text)
    case Method.GET -> !! / "utc"          => clock.currentDateTime.map(s => Response.text(s.toString))
    case req @ Method.POST -> !! / "test1" =>
      req.decode(Decoder.text, true).map { content =>
        Response(data = HttpData.fromText(content))
      }
    case req @ Method.POST -> !! / "test2" =>
      req.decode(Decoder.text).map { content =>
        Response(data = HttpData.fromText(content))
      }
  }

  private val server =
    Server.port(PORT) ++ Server.statusContinue ++ // Setup port
      Server.paranoidLeakDetection ++             // Paranoid leak detection (affects performance)
      Server.app(app)                             // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    // Create a new server
    server.make
      .use(_ =>
        // Waiting for the server to start
        console.putStrLn(s"Server started on port $PORT")

        // Ensures the server doesn't die after printing
          *> ZIO.never,
      )
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(nThreads))
      .exitCode
  }
}

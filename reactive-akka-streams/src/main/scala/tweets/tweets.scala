package tweets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.{ApplicationRunner, SpringApplication}
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.RequestPredicates._
import org.springframework.web.reactive.function.server.RouterFunctions._
import org.springframework.web.reactive.function.server.ServerResponse._
import reactor.core.publisher.Flux

import scala.beans.BeanProperty
import scala.collection.JavaConverters

@SpringBootApplication
class Application {

  @Bean
  def init(tr: TweetRepository): ApplicationRunner = args => {
    val viktor = Author("viktorklang")
    val jonas = Author("jboner")
    val josh = Author("starbuxman")
    val tweets = Flux.just(
      Tweet("Woot, Konrad will be talking about #Enterprise #Integration done right! #akka #alpakka", viktor),
      Tweet("#scala implicits can easily be used to model Capabilities, but can they encode Obligations easily?\n\n* Easy as in: ergonomically.", viktor),
      Tweet("This is so cool! #akka", viktor),
      Tweet("Cross Data Center replication of Event Sourced #Akka Actors is soon available (using #CRDTs, and more).", jonas),
      Tweet("a reminder: @SpringBoot lets you pair-program with the #Spring team.", josh),
      Tweet("whatever your next #platform is, don't build it yourself. \n\nEven companies with the $$ and motivation to do it fail. a LOT.", josh)
    )
    tr
      .deleteAll()
      .thenMany(tr.saveAll(tweets))
      .thenMany(tr.findAll())
      .subscribe((t: Tweet) => println(
        s"""=====================================================
           |@${t.author.handle} ${t.hashtags}
           |${t.text}
         """.stripMargin
      ))
  }
}

@Service
class TweetService(tr: TweetRepository, am: ActorMaterializer) {

  def tweets(): Publisher[Tweet] = tr.findAll()

  def hashtags(): Publisher[HashTag] =
    Source
      .fromPublisher(tweets())
      .map(t => JavaConverters.asScalaSet(t.hashtags).toSet)
      .reduce((a, b) => a ++ b)
      .mapConcat(identity)
      .runWith(Sink.asPublisher(true)) {
        am
      }
}

@Configuration
class TweetRouteConfiguration(tweetService: TweetService) {

  @Bean
  def routes() =
    route(GET("/tweets"), _ => ok().body(tweetService.tweets(), classOf[Tweet]))
      .andRoute(GET("/hashtags/unique"), _ => ok().body(tweetService.hashtags(), classOf[HashTag]))

}

/*
@RestController
class TweetRestController(ts: TweetService) {

  @GetMapping(Array("/hashtags/unique"))
  def hashtags(): Publisher[HashTag] = ts.hashtags()

  @GetMapping(Array("/tweets"))
  def tweets(): Publisher[Tweet] = ts.tweets()
}
*/

object Application extends App {
  SpringApplication.run(classOf[Application], args: _*)
}


trait TweetRepository extends ReactiveMongoRepository[Tweet, String]

@Document
case class Author(@BeanProperty @Id handle: String)

@Document
case class HashTag(@BeanProperty @Id tag: String)

@Document
case class Tweet(@BeanProperty @Id text: String, @BeanProperty author: Author) {

  @BeanProperty
  var hashtags: java.util.Set[HashTag] = JavaConverters.setAsJavaSet(
    text
      .split(" ")
      .collect {
        case t if t.startsWith("#") => HashTag(t.replaceAll("[^#\\w]", "").toLowerCase())
      }
      .toSet
  )

}
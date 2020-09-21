package com.howtographql.scala.sangria

import akka.http.scaladsl.model.DateTime
import com.howtographql.scala.sangria.models.{DateTimeCoerceViolation, Link, User}
import sangria.schema._
import sangria.execution.deferred.{DeferredResolver, Fetcher, HasId}
import sangria.ast.StringValue

// What we are able to query for
// Interprets how data fetched and which data source it could use


object GraphQLSchema {
  // custom scalar declare with implicit
  // scalars make possible to parse values type you want to
  implicit val GraphQLDateTime = ScalarType[DateTime](
    "DateTime", // this name will be used in schema
    coerceOutput = (dt, _) => dt.toString, // used to output data (DateTime to string)
    coerceInput = { // need a partial function with Value as a single argument. In our case we're parsing from StringValue
      case StringValue(dt, _, _) => DateTime.fromIsoDateTimeString(dt).toRight(DateTimeCoerceViolation)
      case _ => Left(DateTimeCoerceViolation)
    },
    coerceUserInput = { // converts literal (almost always string)
      case s: String => DateTime.fromIsoDateTimeString(s).toRight(DateTimeCoerceViolation)
      case _ => Left(DateTimeCoerceViolation)
    }
  )
  // Both  coerceInput and coerceUserInput functions should respond with Either
  // Right should consist an object of expected type
  // In the case of failure, Left should contain Violation Subtype

  // Definition of ObectType for Link Class
  val LinkType = ObjectType[Unit, Link](
    "Link", // name of schema
    fields[Unit, Link](
      Field("id", IntType, resolve = _.value.id),
      Field("url", StringType, resolve = _.value.url),
      Field("description", StringType, resolve = _.value.description),
      Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)
    )
    // fields = name of fields, function you want to expose
    // every field has to contain a resolve function
    // resolve = how to retrieve data for field
  )

  val UserType = ObjectType[Unit, User](
    name = "User",
    fields[Unit, User](
      Field("id", IntType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email),
      Field("password", StringType, resolve = _.value.password),
      Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)
    )
  )
  implicit  val userHasId = HasId[User, Int](_.id) // HasId typeClass
  val usersFetcher = Fetcher(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getUsers(ids)
  )


  val Id = Argument("id", IntType)
  val Ids = Argument("ids", ListInputType(IntType))

  implicit val linkHasId = HasId[Link, Int](_.id)
  
  // Fetcher - mechanism for batch reterieval of objects from their sources (e.g. DB or external API)
  // Fetcher - specialized verision of Deferred resolver (high level API)
  // Optimizes resolution of fetched entities based on ID or relation
  // Deduplicates entities and cahces results
  // Optimizees the query before call
  // Gathers all the data it should fetch, then execute queries
  // Fetcher needs something (`HasId`) to extract from entities,
  // 1) either by implicit val in companion object of model
  // 2) or explicitly passing it in
  // 3) implicit val in the same context
  val linksFetcher = Fetcher(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getLinks(ids)
  )

  val Resolver = DeferredResolver.fetchers(linksFetcher, usersFetcher)

  // QueryType is a top level object of schema
  val QueryType = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks",
        ListType(LinkType),
        resolve = c => c.ctx.dao.allLinks
      ),
      Field("link",
        OptionType(LinkType), // expected output type
        arguments = Id :: Nil,  // Arguments = list of expected arguments defined by name and type, // expecting id argument of type int
        resolve = c => linksFetcher.deferOpt(c.arg(Id))
      ),
      Field("links",
        ListType(LinkType),
        arguments = Ids :: Nil, // InputType are used to passed incoming data, ObjectType 9mostly) for outgoing data
        resolve = c => linksFetcher.deferSeq(c.arg(Ids)) // Pass argument to resolver
      ),
      Field("users",
        ListType(UserType),
        arguments = List(Ids),
        resolve = c => usersFetcher.deferSeq(c.arg(Ids))
      )
    )
  )

  val SchemaDefinition = Schema(QueryType)
}

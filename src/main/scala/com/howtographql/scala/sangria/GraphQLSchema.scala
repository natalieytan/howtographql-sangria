package com.howtographql.scala.sangria

import akka.http.scaladsl.model.DateTime
import com.howtographql.scala.sangria.models._
import sangria.ast.StringValue
import sangria.execution.deferred.{DeferredResolver, Fetcher, Relation, RelationIds}
import sangria.marshalling.FromInput
import sangria.marshalling.sprayJson._
import sangria.schema._
import sangria.util.tag.@@
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat


// What we are able to query for
// Interprets how data fetched and which data source it could use


object GraphQLSchema {
  // Sangria needs to read part of a JSON like structure and covert it to case class
  implicit val authProviderEmailFormat: RootJsonFormat[AuthProviderEmail] = jsonFormat2(AuthProviderEmail)
  implicit val authProviderSignupDataFormat: RootJsonFormat[AuthProviderSignupData] = jsonFormat1(AuthProviderSignupData)

  // custom scalar declare with implicit
  // scalars make possible to parse values type you want to
  implicit val GraphQLDateTime: ScalarType[DateTime] = ScalarType[DateTime](
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

  val IdentifiableType: InterfaceType[Unit, Identifiable] = InterfaceType(
    "Identifiable",
    fields[Unit, Identifiable](
      Field("id", IntType, resolve = _.value.id)
    )
  )

  // lazy and type annotate to get around circular reference in Object ype declaration
  // Definition of ObjectType for Link Class
  lazy val LinkType: ObjectType[Unit, Link] = ObjectType[Unit, Link](
    "Link", // name of schema
    interfaces[Unit, Link](IdentifiableType),
    () => fields[Unit, Link](
      Field("url", StringType, resolve = _.value.url),
      Field("description", StringType, resolve = _.value.description),
      Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt),
      Field("postedBy", UserType, resolve = c => usersFetcher.defer(c.value.postedBy)),
      Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByLinkRel, c.value.id))
    )
    // fields = name of fields, function you want to expose
    // every field has to contain a resolve function
    // resolve = how to retrieve data for field
  )

  // lazy and type annotate to get around circular reference in Object ype declaration
  lazy val UserType: ObjectType[Unit, User] = ObjectType[Unit, User](
    name = "User",
    interfaces[Unit, User](IdentifiableType),
    () => fields[Unit, User](
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email),
      Field("password", StringType, resolve = _.value.password),
      Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt),
      Field("links", ListType(LinkType),
        resolve = c => linksFetcher.deferRelSeq(linkByUserRel, c.value.id)
      ),
      Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByUserRel, c.value.id))
    )
  )
  // .deferRel needs 2 arguments
  // 1) relation object = first argument
  // 2) function which will get mapping value from entity

  lazy val VoteType: ObjectType[Unit, Vote] = ObjectType[Unit, Vote](
    name = "Vote",
    interfaces[Unit, Vote](IdentifiableType),
    () => fields[Unit, Vote](
      Field("userId", IntType, resolve = _.value.id),
      Field("linkId", IntType, resolve = _.value.linkId),
      Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt),
      Field("user", UserType, resolve = c => usersFetcher.defer(c.value.userId)),
      Field("link", LinkType, resolve = c => linksFetcher.defer(c.value.linkId))
    )
  )

  implicit val AuthProviderEmailInputType: InputObjectType[AuthProviderEmail] = InputObjectType[AuthProviderEmail](
    "AUTH_PROVIDER_EMAIL",
    List(
      InputField("email", StringType),
      InputField("password", StringType)
    )
  )

  lazy val AuthProviderSignupDataInputType: InputObjectType[AuthProviderSignupData] = InputObjectType[AuthProviderSignupData](
    "AuthProviderSignupData",
    List(
      InputField("email", AuthProviderEmailInputType)
    )
  )


  // SimpleRelation
  // 2 Arguments: 1) name, 2) function which extracts sequence of user Ids from link entity
  // In relation we always have to return sequence
  val linkByUserRel: Relation[Link, Link, Int] = Relation[Link, Int]("byUser", l => Seq(l.postedBy))

  val voteByUserRel: Relation[Vote, Vote, Int] = Relation[Vote, Int]("byUser", v => Seq(v.userId))

  val voteByLinkRel: Relation[Vote, Vote, Int] = Relation[Vote, Int]("byLink", v => Seq(v.linkId))

  val usersFetcher: Fetcher[MyContext, User, User, Int] = Fetcher(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getUsers(ids)
  )

  val votesFetcher: Fetcher[MyContext, Vote, Vote, Int] = Fetcher.rel(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getVotes(ids),
    (ctx: MyContext, ids: RelationIds[Vote]) => ctx.dao.getVotesByRelationIds(ids),
  )

  // ids(voteByUserRel) extracts users ids and passes it to the db function
  // we can pass ids down to the db function and let DAO decide which field it should use to filter

  // Fetcher - mechanism for batch retrieval of objects from their sources (e.g. DB or external API)
  // Fetcher - specialized version of Deferred resolver (high level API)
  // Optimizes resolution of fetched entities based on ID or relation
  // Deduplicate entities and caches results
  // Optimizes the query before call
  // Gathers all the data it should fetch, then execute queries
  // Fetcher needs something (`HasId`) to extract from entities,
  // 1) either by implicit val in companion object of model
  // 2) or explicitly passing it in
  // 3) implicit val in the same context
  val linksFetcher: Fetcher[MyContext, Link, Link, Int] = Fetcher.rel(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getLinks(ids),
    (ctx: MyContext, ids: RelationIds[Link]) => ctx.dao.getLinksByUserIds(ids(linkByUserRel))
  )
  // .rel needs second function to be passed in as argument
  // used for fetching related data from datasource
  // ids(linksByUserRel) extracts user ids defined relation


  val Id: Argument[Int] = Argument("id", IntType)
  val Ids: Argument[Seq[Int @@ FromInput.CoercedScalaResult]] = Argument("ids", ListInputType(IntType))
  val NameArg: Argument[String] = Argument("name", StringType)
  val AuthProviderArg: Argument[AuthProviderSignupData] = Argument("authProvider", AuthProviderSignupDataInputType)
  val UrlArg: Argument[String] = Argument("url", StringType)
  val DescArg: Argument[String] = Argument("description", StringType)
  val PostedByArg: Argument[Int] = Argument("postedById", IntType)
  val LinkIdArg: Argument[Int] = Argument("linkId", IntType)
  val UserIdArg: Argument[Int] = Argument("userId", IntType)

  val Resolver: DeferredResolver[MyContext] = DeferredResolver.fetchers(linksFetcher, usersFetcher, votesFetcher)

  // QueryType is a top level object of schema
  val QueryType: ObjectType[MyContext, Unit] = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks",
        ListType(LinkType),
        resolve = c => c.ctx.dao.allLinks
      ),
      Field("link",
        OptionType(LinkType), // expected output type
        arguments = Id :: Nil, // Arguments = list of expected arguments defined by name and type, // expecting id argument of type int
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
      ),
      Field("votes",
        ListType(VoteType),
        arguments = List(Ids),
        resolve = c => votesFetcher.deferSeq(c.arg(Ids))
      )
    )
  )

  val Mutation: ObjectType[MyContext, Unit] = ObjectType(
    "Mutation",
    fields[MyContext, Unit](
      Field(
        "createUser",
        UserType,
        arguments = NameArg :: AuthProviderArg :: Nil,
        resolve = c => c.ctx.dao.createUser(c.arg(NameArg), c.arg(AuthProviderArg))
      ),
      Field(
        "createLink",
        LinkType,
        arguments = UrlArg :: DescArg :: PostedByArg :: Nil,
        resolve = c => c.ctx.dao.createLink(c.arg(UrlArg), c.arg(DescArg), c.arg(PostedByArg))
      ),
      Field(
        "createVote",
        VoteType,
        arguments = LinkIdArg :: UserIdArg :: Nil,
        resolve = c => c.ctx.dao.createVote(c.arg(LinkIdArg), c.arg(UserIdArg))
      )
    )
  )

  // All mutations are optional so you have to wrap it in Some
  val SchemaDefinition: Schema[MyContext, Unit] = Schema(QueryType, Some(Mutation))
}

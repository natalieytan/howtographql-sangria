package com.howtographql.scala.sangria

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.howtographql.scala.sangria.models.{AuthenticationException, AuthorizationException}
import sangria.ast.Document
import sangria.execution.{ExceptionHandler => EHandler, _}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, JsValue}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


object GraphQLServer {

  // db connection
  private val dao = DBSchema.createDatabase

  // used in routing of HTTP server
  def endpoint(requestJSON: JsValue)(implicit ec: ExecutionContext): Route = {

    // Json object extracted from root
    // Usual expected structure
    //    {
    //      query: {},
    //      variables: {},
    //      operationName: ""
    //    }
    val JsObject(fields) = requestJSON

    // Extract query from request
    val JsString(query) = fields("query")

    // Sangria provides QueryParser.parse to parse query
    // Returns Success / failure
    QueryParser.parse(query) match {
      case Success(queryAst) =>
        // Get operationName
        val operation = fields.get("operationName") collect {
          case JsString(op) => op
        }

        // Get variables
        val variables = fields.get("variables") match {
          case Some(obj: JsObject) => obj
          case _ => JsObject.empty
        }

        // All 3 objects passed to execution function
        complete(executeGraphQLQuery(queryAst, operation, variables))
      case Failure(error) =>
        complete(BadRequest, JsObject("error" -> JsString(error.getMessage)))
    }

  }

  // Custom Exception Handler
  // Needs a partial function which convert type of exception into a HandledException
  // This exception is internally converted into proper JSON response and sent back to the client
  val ErrorHandler: EHandler = EHandler {
    case (_, AuthenticationException(message)) => HandledException(message)
    case (_, AuthorizationException(message)) => HandledException(message)
  }

  private def executeGraphQLQuery(query: Document, operation: Option[String], vars: JsObject)(implicit ec: ExecutionContext) = {
    // Where query is executed
    Executor.execute(
      GraphQLSchema.SchemaDefinition, // Contains our Schema
      query, // read from request
      MyContext(dao), // context object
      variables = vars, // read from request
      operationName = operation, // read from request
      deferredResolver = GraphQLSchema.Resolver,
      exceptionHandler = ErrorHandler,
      middleware = AuthMiddleware :: Nil
    ).map(OK -> _)
      .recover {
        case error: QueryAnalysisError => BadRequest -> error.resolveError
        case error: ErrorWithResolver => InternalServerError -> error.resolveError
      }
  }

}
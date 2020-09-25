package com.howtographql.scala.sangria

import com.howtographql.scala.sangria.models.Authorized
import sangria.execution.{Middleware, MiddlewareBeforeField, MiddlewareQueryContext}
import sangria.schema.{Action, Context}

object AuthMiddleware extends Middleware[MyContext] with MiddlewareBeforeField[MyContext] {
  override type QueryVal = Unit
  override type FieldVal = Unit

  override def beforeQuery(context: MiddlewareQueryContext[MyContext, _, _]): Unit = ()

  override def afterQuery(queryVal: QueryVal, context: MiddlewareQueryContext[MyContext, _, _]): Unit = ()

  override def beforeField(queryVal: QueryVal, mctx: MiddlewareQueryContext[MyContext, _, _], ctx: Context[MyContext, _]): (Unit, Option[Action[MyContext, _]]) = {
    val requireAuth = ctx.field.tags.contains(Authorized)
    // tries to read Authorized Field Tag

    if (requireAuth) {
      ctx.ctx.ensureAuthenticated()
    }
    // runs ensureAuthenticated on the context

    // Allows Sangria to continue if no exception
    continue
  }
}

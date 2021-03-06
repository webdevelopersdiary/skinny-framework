package skinny.controller.feature

import org.scalatra.SkinnyScalatraBase
import skinny.logging.Logging

/**
 * Angular.js Cross Site Request Forgery (XSRF) Protection support.
 *
 * https://docs.angularjs.org/api/ng/service/$http#cross-site-request-forgery-xsrf-protection
 */
trait AngularXSRFProtectionFeature extends AngularXSRFCookieProviderFeature {

  self: SkinnyScalatraBase with ActionDefinitionFeature with BeforeAfterActionFeature with RequestScopeFeature with Logging =>

  /**
   * Enabled if true.
   */
  private[this] var forgeryProtectionEnabled: Boolean = false

  /**
   * Excluded actions.
   */
  private[this] val forgeryProtectionExcludedActionNames = new scala.collection.mutable.ArrayBuffer[Symbol]

  /**
   * Included actions.
   */
  private[this] val forgeryProtectionIncludedActionNames = new scala.collection.mutable.ArrayBuffer[Symbol]

  /**
   * Cookie name.
   */
  override protected def xsrfCookieName: String = super.xsrfCookieName

  /**
   * Header name.
   */
  protected def xsrfHeaderName: String = AngularJSSpecification.xsrfHeaderName

  /**
   * Declarative activation of XSRF protection. Of course, highly inspired by Ruby on Rails.
   *
   * @param only should be applied only for these action methods
   * @param except should not be applied for these action methods
   */
  def protectFromForgery(only: Seq[Symbol] = Nil, except: Seq[Symbol] = Nil) {
    forgeryProtectionEnabled = true
    forgeryProtectionIncludedActionNames ++= only
    forgeryProtectionExcludedActionNames ++= except
  }

  /**
   * Overrides to skip execution when the current request matches excluded patterns.
   */
  def handleAngularForgery() {
    if (forgeryProtectionEnabled) {
      logger.debug {
        s"""
        | ------------------------------------------
        |  [Angular XSRF Protection Enabled]
        |  method      : ${request.getMethod}
        |  requestPath : ${requestPath}
        |  actionName  : ${currentActionName}
        |  only        : ${forgeryProtectionIncludedActionNames.mkString(", ")}
        |  except      : ${forgeryProtectionExcludedActionNames.mkString(", ")}
        | ------------------------------------------
        |""".stripMargin
      }

      currentActionName.map { name =>
        val currentPathShouldBeExcluded = forgeryProtectionExcludedActionNames.exists(_ == name)
        if (!currentPathShouldBeExcluded) {
          val allPathShouldBeIncluded = forgeryProtectionIncludedActionNames.isEmpty
          val currentPathShouldBeIncluded = forgeryProtectionIncludedActionNames.exists(_ == name)
          if (allPathShouldBeIncluded || currentPathShouldBeIncluded) {
            handleForgeryIfDetected()
          }
        }
      }.getOrElse {
        handleForgeryIfDetected()
      }
    }
  }

  /**
   * Handles when XSRF is detected.
   */
  def handleForgeryIfDetected(): Unit = halt(403)

  def isForged: Boolean = {
    val unsafeMethod = !request.requestMethod.isSafe
    val headerValue = request.headers.get(xsrfHeaderName)
    val cookieValue = request.cookies.get(xsrfCookieName)
    val neither = (headerValue.isEmpty || cookieValue.isEmpty)
    val differentValue = !headerValue.exists(h => cookieValue.exists(c => c == h))
    unsafeMethod && (neither || differentValue)
  }

  before(isForged) {
    handleAngularForgery()
  }

}

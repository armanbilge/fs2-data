/*
 * Copyright 2023 Lucas Satabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fs2
package data
package json
package interpolators

import cats.syntax.all._

import org.typelevel.literally.Literally

object SelectorInterpolator extends Literally[Selector] {

  def validate(string: String)(using Quotes) = {
    SelectorParser.either(string) match {
      case Left(JsonSelectorException(msg, idx)) => Left(msg)
      case Left(t)                               => Left(t.getMessage)
      case Right(v)                              => Right('{ SelectorParser.either(${ Expr(string) }).toOption.get })
    }
  }

}

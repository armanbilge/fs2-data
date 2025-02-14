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
package csv
package generic

import fs2.data.csv.generic.internal._
import shapeless._

object hlist {

  final implicit def hlistDecoder[T <: HList](implicit cc: Lazy[SeqShapedRowDecoder[T]]): DerivedRowDecoder[T] =
    (row: Row) => cc.value(row)

  final implicit def hlistEncoder[T <: HList](implicit cc: Lazy[SeqShapedRowEncoder[T]]): DerivedRowEncoder[T] =
    (elem: T) => cc.value(elem)

}

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

import cats.effect.IO
import weaver._

object RowDecoderFTest extends SimpleIOSuite {

  case class TwoNumbers(a: Int, b: Int)

  implicit val rowDecoder: RowDecoder[TwoNumbers] = RowDecoder.instance { row =>
    for {
      a <- row.asAt[Int](0)
      b <- row.asAt[Int](1)
    } yield TwoNumbers(a, b)
  }

  implicit val csvRowDecoder: CsvRowDecoder[TwoNumbers, String] = CsvRowDecoder.instance { row =>
    for {
      a <- row.as[Int]("a")
      b <- row.as[Int]("b")
    } yield TwoNumbers(a, b)
  }

  test("can parse CSV with rows that do not convert by an attempting RowDecoder") {
    val rows = List(
      "4,5\n",
      "6,seven"
    )
    Stream
      .emits[IO, String](rows)
      .through(decodeWithoutHeaders[DecoderResult[TwoNumbers]]())
      .compile
      .toList
      .map { result =>
        expect(result.head.isRight) and expect(result.tail.head.isLeft)
      }
  }

  test("can parse CSV with rows that do not convert by an attempting CsvRowDecoder") {
    val rows = List(
      "a,b\n",
      "4,5\n",
      "6,seven"
    )
    Stream
      .emits[IO, String](rows)
      .through(decodeUsingHeaders[DecoderResult[TwoNumbers]]())
      .compile
      .toList
      .map { result =>
        expect(result.head.isRight) and expect(result.tail.head.isLeft)
      }
  }

}

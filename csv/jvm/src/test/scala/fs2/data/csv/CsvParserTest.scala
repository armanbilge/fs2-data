/*
 * Copyright 2021 Lucas Satabin
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

package fs2.data.csv

import io.circe.parser.parse
import fs2._
import fs2.io.file.{Files, Flags, Path}
import cats.effect._
import cats.syntax.all._
import weaver._

object CsvParserTest extends SimpleIOSuite {

  private val testFileDir: Path = Path("csv/jvm/src/test/resources/csv-spectrum/csvs/")

  lazy val allExpected: Stream[IO, (Path, List[Map[String, String]])] =
    Files[IO]
      .list(testFileDir)
      .evalMap { path =>
        val name = path.fileName.toString.stripSuffix(".csv")
        Files[IO]
          .readAll(Path(s"csv/jvm/src/test/resources/csv-spectrum/json/$name.json"), 1024, Flags.Read)
          .through(text.utf8.decode)
          .compile
          .string
          .flatMap { rawExpected =>
            parse(rawExpected)
              .flatMap(_.as[List[Map[String, String]]])
              .liftTo[IO]
          }
          .tupleLeft(path)
      }

  loggedTest("Standard test suite should pass") { log =>
    allExpected
      .evalTap { case (path, _) => log.info(path.fileName.toString) }
      .evalMap { case (path, expected) =>
        Files[IO]
          .readAll(path, 1024, Flags.Read)
          .through(fs2.text.utf8.decode)
          .through(decodeUsingHeaders[CsvRow[String]]())
          .compile
          .toList
          .map(_.map(_.toMap))
          .map(actual => expect.eql(expected, actual))
      }
      .compile
      .foldMonoid
  }

  loggedTest("Standard test suite files should be encoded and parsed correctly") { log =>
    allExpected
      .evalTap { case (path, _) => log.info(path.fileName.toString) }
      .evalMap { case (path, expected) =>
        Stream
          .emits(expected)
          .map(m => CsvRow.fromListHeaders(m.toList))
          .unNone
          .through[IO, String](encodeUsingFirstHeaders[CsvRow[String]]())
          .covary[IO]
          .through(decodeUsingHeaders[CsvRow[String]]())
          .compile
          .toList
          .map(_.map(_.toMap))
          .map(reencoded => expect.eql(expected, reencoded))
      }
      .compile
      .foldMonoid
  }

  test("Parser should handle literal quotes if specified") {
    val content =
      """name,age,description
        |John Doe,47,no quotes
        |Jane Doe,50,"entirely quoted"
        |Bob Smith,80,"starts with" a quote
        |Alice Grey,78,contains "a quote
        |""".stripMargin

    val expected = List(
      Map("name" -> "John Doe", "age" -> "47", "description" -> "no quotes"),
      Map("name" -> "Jane Doe", "age" -> "50", "description" -> "\"entirely quoted\""),
      Map("name" -> "Bob Smith", "age" -> "80", "description" -> "\"starts with\" a quote"),
      Map("name" -> "Alice Grey", "age" -> "78", "description" -> "contains \"a quote")
    )

    Stream
      .emit(content)
      .covary[IO]
      .through(decodeUsingHeaders[CsvRow[String]](',', QuoteHandling.Literal))
      .compile
      .toList
      .map(_.map(_.toMap))
      .map(actual => expect.eql(expected, actual))
  }
}

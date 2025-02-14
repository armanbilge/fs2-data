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

package fs2.data.csv

import fs2.data.csv.internals.RowWriter

import weaver._

object RowWriterTest extends SimpleIOSuite {

  pureTest("RowWriter should escape according to the given escape mode") {
    // separator
    expect.eql(RowWriter.encodeColumn(',', EscapeMode.Auto)(","), "\",\"") and
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Always)(","), "\",\"") and
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Never)(","), ",") and
      // quotes
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Auto)("\""), "\"\"\"\"") and
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Always)("\""), "\"\"\"\"") and
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Never)("\""), "\"") and
      // normal string
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Auto)("test"), "test") and
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Always)("test"), "\"test\"") and
      expect.eql(RowWriter.encodeColumn(',', EscapeMode.Never)("test"), "test")
  }

}

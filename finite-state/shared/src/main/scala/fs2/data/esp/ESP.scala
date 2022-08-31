/*
 * Copyright 2019-2022 Lucas Satabin
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
package esp

import cats.syntax.all._
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import fs2.data.matching.Table

sealed trait Rhs[+Out]
object Rhs {
  case class Call[Out](q: Int, depth: Depth, params: List[Rhs[Out]]) extends Rhs[Out]
  case class Param[Out](n: Int) extends Rhs[Out]
  case object Epsilon extends Rhs[Nothing]
  case class Tree[Out](open: Out, inner: Rhs[Out], close: Out) extends Rhs[Out]
  case class Leaf[Out](value: Out) extends Rhs[Out]
  case class Concat[Out](fst: Rhs[Out], snd: Rhs[Out]) extends Rhs[Out]

  def epsilon[Out]: Rhs[Out] = Epsilon

}

sealed trait Depth {
  def apply(d: Int): Int =
    this match {
      case Depth.Value(d)  => d
      case Depth.Copy      => d
      case Depth.Increment => d + 1
      case Depth.Decrement => d - 1
    }
}
object Depth {
  case class Value(d: Int) extends Depth
  case object Copy extends Depth
  case object Increment extends Depth
  case object Decrement extends Depth
}

sealed trait Expr[+Out]
object Expr {
  case class Call[Out](q: Int, depth: Int, params: List[Expr[Out]]) extends Expr[Out]
  case object Epsilon extends Expr[Nothing]
  case class Open[Out](open: Out, next: Expr[Out]) extends Expr[Out]
  case class Close[Out](close: Out, next: Expr[Out]) extends Expr[Out]
  case class Leaf[Out](value: Out, next: Expr[Out]) extends Expr[Out]
  case class Concat[Out](fst: Expr[Out], snd: Expr[Out]) extends Expr[Out]

  def concat[Out](e1: Expr[Out], e2: Expr[Out]): Expr[Out] =
    (e1, e2) match {
      case (Epsilon, _)           => e2
      case (_, Epsilon)           => e1
      case (Open(o, Epsilon), _)  => Open(o, e2)
      case (Close(c, Epsilon), _) => Close(c, e2)
      case (Leaf(v, Epsilon), _)  => Leaf(v, e2)
      case (_, _)                 => Concat(e1, e2)
    }
}

/** An Event Stream Processor is a generalization of the one defined in _Streamlining Functional XML Processing_.
  * It encodes its recognized patterns into a [[fs2.data.matching.DecisionTree Decision Tree]], including the state and depth. This flexibility allows for easily implementing
  * catch all rules, no matter what the state or depth is.
  */
private[data] class ESP[F[_], T, In, Out](init: Int, val params: Map[Int, List[Int]], val rules: T)(implicit
    F: RaiseThrowable[F],
    T: Table[T, Input[In], Rhs[Out]]) {

  def step(env: Map[Int, Expr[Out]], e: Expr[Out], in: Option[In]): Pull[F, Nothing, Expr[Out]] =
    e match {
      case Expr.Call(q, d, args) =>
        params.get(q).liftTo[Pull[F, Nothing, *]](new ESPException(s"unknown state $q")).flatMap { params =>
          if (params.size === args.size) {
            args
              .zip(params)
              .foldLeftM(env) { case (env, (arg, param)) =>
                step(env, arg, in).map { rhs =>
                  env.updated(param, rhs)
                }
              }
              .flatMap { env =>
                T.get(rules)(Input(q, d, in))
                  .liftTo[Pull[F, Nothing, *]](
                    new ESPException(s"no rule found for state $q at depth $d for input $in"))
                  .flatMap(eval(env, d, _))
              }
          } else {
            Pull.raiseError(new ESPException(
              s"wrong number of argument given in state $q reading input $in (expected ${params.size} but got ${args.size})"))
          }
        }
      case Expr.Epsilon =>
        Pull.pure(Expr.Epsilon)
      case Expr.Open(o, next) =>
        step(env, next, in).map(Expr.Open(o, _))
      case Expr.Close(c, next) =>
        step(env, next, in).map(Expr.Close(c, _))
      case Expr.Leaf(v, next) =>
        step(env, next, in).map(Expr.Leaf(v, _))
      case Expr.Concat(e1, e2) =>
        (step(env, e1, in), step(env, e2, in)).mapN(Expr.concat(_, _))
    }

  def eval(env: Map[Int, Expr[Out]], depth: Int, rhs: Rhs[Out]): Pull[F, Nothing, Expr[Out]] =
    rhs match {
      case Rhs.Call(q, d, params) =>
        params.traverse(eval(env, depth, _)).map(Expr.Call(q, d(depth), _))
      case Rhs.Param(i) =>
        env.get(i).liftTo[Pull[F, Nothing, *]](new ESPException(s"unknown parameter $i"))
      case Rhs.Epsilon =>
        Pull.pure(Expr.Epsilon)
      case Rhs.Tree(open, inner, close) =>
        eval(env, depth, inner).map(inner => Expr.Open(open, Expr.concat(inner, Expr.Close(close, Expr.Epsilon))))
      case Rhs.Leaf(v) =>
        Pull.pure(Expr.Leaf(v, Expr.Epsilon))
      case Rhs.Concat(rhs1, rhs2) =>
        (eval(env, depth, rhs1), eval(env, depth, rhs2)).mapN(Expr.concat(_, _))
    }

  private def squeeze(e: Expr[Out]): (Expr[Out], List[Out]) =
    e match {
      case Expr.Call(_, _, _) => (e, Nil)
      case Expr.Epsilon       => (Expr.Epsilon, Nil)
      case Expr.Open(o, e) =>
        val (e1, s1) = squeeze(e)
        (e1, o :: s1)
      case Expr.Close(c, e) =>
        val (e1, s1) = squeeze(e)
        (e1, c :: s1)
      case Expr.Leaf(v, e) =>
        val (e1, s1) = squeeze(e)
        (e1, v :: s1)
      case Expr.Concat(Expr.Epsilon, e2) =>
        val (e21, s2) = squeeze(e2)
        (e21, s2)
      case Expr.Concat(e1, e2) =>
        val (e11, s1) = squeeze(e1)
        (Expr.concat(e11, e2), s1)
    }

  def squeezeAll(e: Expr[Out]): (Expr[Out], List[Out]) = {
    @tailrec
    def loop(e: Expr[Out], acc: ListBuffer[Out]): (Expr[Out], List[Out]) =
      e match {
        case Expr.Epsilon | Expr.Call(_, _, _) => (e, acc.result())
        case _ =>
          val (e1, s) = squeeze(e)
          loop(e1, acc ++= s)
      }
    loop(e, new ListBuffer)
  }

  def transform(chunk: Chunk[In],
                idx: Int,
                rest: Stream[F, In],
                env: Map[Int, Expr[Out]],
                e: Expr[Out],
                chunkAcc: ListBuffer[Out]): Pull[F, Out, Unit] =
    if (idx >= chunk.size) {
      Pull.output(Chunk.seq(chunkAcc.result())) >> rest.pull.uncons.flatMap {
        case Some((hd, tl)) =>
          chunkAcc.clear()
          transform(hd, 0, tl, env, e, chunkAcc)
        case None =>
          step(env, e, none).map(squeezeAll(_)).flatMap { case (e, s) =>
            e match {
              case Expr.Epsilon => Pull.output(Chunk.seq(s))
              case _            => Pull.raiseError(new ESPException(s"unexpected end of input $e"))
            }
          }
      }
    } else {
      step(env, e, chunk(idx).some).map(squeeze(_)).flatMap { case (e, s) =>
        transform(chunk, idx + 1, rest, env, e, chunkAcc ++= s)
      }
    }

  def pipe: Pipe[F, In, Out] =
    transform(Chunk.empty, 0, _, Map.empty, Expr.Call(init, 0, Nil), new ListBuffer).stream

}

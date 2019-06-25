import scala.util.{Failure, Success, Try}

object ParsersTypes {
  //(parsedElement, notConsumed)
  type ParserOutput[T] = (T, String)
  type ParserResult[T] = Try[ParserOutput[T]]
  type ParserCondition[T] = T => Boolean
}
import ParsersTypes._

class Parser[T](criterion: String => ParserResult[T]) {
  def apply(input: String): ParserResult[T] = criterion(input)

  def <|>(anotherParser: Parser[T]): Parser[T] = new Parser[T](input => this(input).orElse(anotherParser(input)))

  def <>[U](anotherParser: Parser[U]): Parser[(T, U)] = new Parser[(T,U)](
    this(_).flatMap { case (parsedElement, notConsumed) => anotherParser(notConsumed).map(parserOutput => ((parsedElement, parserOutput._1), parserOutput._2)) }
  )

  def ~>(anotherParser: Parser[T]): Parser[T] = new Parser[T](
    this(_).flatMap { case (_, notConsumed) => anotherParser(notConsumed) }
  )

  def <~(anotherParser: Parser[T]): Parser[T] = new Parser[T](
    this(_).flatMap{ case (parsedElement, notConsumed) => anotherParser(notConsumed).map(parserOutput => (parsedElement, parserOutput._2)) }
  )

  def satisfies(condition: ParserCondition[T]) = new Parser[T](input =>
    this(input).filter(parserOutput => condition(parserOutput._1)).orElse(Failure(new NotSatisfiesException(condition, input)))
  )

  def opt: Parser[Option[T]] = new Parser[Option[T]](
    input => this(input).map{ case (parsedElement, notConsumed) => (Some(parsedElement), notConsumed) }.orElse(Success(None, input))
  )

  def * : Parser[List[T]] = new Parser[List[T]](input =>
    this(input).transform(
      {
        case (parsedElement, "") => Success(List(parsedElement), "")
        case (parsedElement, notConsumed) =>
          this.*(notConsumed).map{ case (parsed, stillNotConsumed) => (parsedElement :: parsed, stillNotConsumed) } 
      },
      _ => Success(List(), input)
    )
  )

  def + = new Parser[List[T]](
    this(_).flatMap{ case (parsedElement, notConsumed) => this.*(notConsumed).map { case (parsed, stillNotConsumed) => (parsedElement :: parsed, stillNotConsumed) } }
  )
}

class NonEmptyInputParser[T](criterion: String => ParserResult[T]) extends Parser[T](criterion) {
  override def apply(input: String): ParserResult[T] =
    if (input.isEmpty) Failure(new EmptyStringException) else super.apply(input)
}

case object anyChar extends NonEmptyInputParser[Char](input => Success(input.head, input.tail))

class anyCharWithCondition(condition: ParserCondition[Char], exception: String => Throwable) extends NonEmptyInputParser[Char](input =>
  anyChar.satisfies(condition)(input)
    .orElse(Failure(exception(input))))

case class char(char: Char) extends anyCharWithCondition(parsed => parsed == char, new CharacterNotFoundException(char, _))

case object void extends NonEmptyInputParser[Unit](input => Success((), input.tail))

case object letter extends anyCharWithCondition(_.isLetter, new NotALetterException(_))

case object digit extends anyCharWithCondition(_.isDigit, new NotADigitException(_))

case object alphaNum extends NonEmptyInputParser[Char](
  input => (letter <|> digit)(input)
    .orElse(Failure(new NotAnAlphaNumException(input)))
)

case class string(string: String) extends Parser[String](
  input =>
    if (input.startsWith(string))
      Success(string, input.slice(string.length, input.length))
    else
      Failure(new NotTheRightStringException(string, input))
)

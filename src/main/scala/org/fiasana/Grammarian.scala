package org.fiasana

import org.clapper.argot._
import opennlp.scalabha.log.SimpleLogger
import opennlp.scalabha.tree.MultiLineTreeParser
import opennlp.scalabha.model.TreeNode
import java.io.{PrintStream, File, OutputStreamWriter, BufferedWriter}

case class Observation(tag: String, observation: Any, count: Int, source: String) {}

case class NonTerminalGrammar() extends Grammar {
  def getObservationKeyString(key: Any): String = key.asInstanceOf[Iterable[String]].mkString(" ")
}

case class TerminalGrammar() extends Grammar {
  def getObservationKeyString(key: Any): String = key.toString
}

abstract case class Grammar() {
  private var grammar: Map[String, Map[Any, (Int, List[String])]]
  = Map().withDefaultValue(Map().withDefaultValue((0, Nil)))
  private var lengthOfObsKey = 0

  val addObservation: (String, Any, Int, String) => Unit =
    (tag, observKey, count, source) => {
      lengthOfObsKey = math.max(lengthOfObsKey, getObservationKeyString(observKey).length)
      grammar += ((tag, grammar(tag) + ((observKey, {
        val (oldCount, oldSources) = grammar(tag)(observKey)
        (count + oldCount, source :: oldSources)
      }))))
    }

  private val addObservations: (String, Any, Int, List[String]) => Unit =
    (tag, observKey, count, sources) => {
      lengthOfObsKey = math.max(lengthOfObsKey, getObservationKeyString(observKey).length)
      grammar += ((tag, grammar(tag) + ((observKey, {
        val (oldCount, oldSources) = grammar(tag)(observKey)
        (count + oldCount, sources ::: oldSources)
      }))))
    }

  def getGrammar = grammar

  val foldIn: (Grammar) => Unit =
    (otherGrammar) => {
      val oGram: Map[String, Map[Any, (Int, List[String])]] = otherGrammar.getGrammar
      for ((oTag, oObservations) <- oGram) {
        for ((oObservKey, (oCount, oSources)) <- oObservations) {
          addObservations(oTag, oObservKey, oCount, oSources)
        }
      }
    }

  def getObservationKeyString(key: Any): String

  def toJSONString: String = {
    "{ %s\n}".format((for ((tag, observations) <- grammar.toList.sortBy(_._1)) yield {
      "%5s : { ".format(tag) + observations.map {
        case (key, count) => ("%-"+lengthOfObsKey+"s: \"%s\"").format("\""+getObservationKeyString(key)+"\"",count)
      }.mkString("\n          , ") + "\n          }\n"
    }).mkString("\n, "))
  }

  val toCSVString: String = {
    ""
  }
}

object Grammarian {
  final val ALLOWED_LANGUAGES = List("kin", "mlg", "eng")
  final val IGNORE_DIRS = List("src")

  import ArgotConverters._

  val parser = new ArgotParser(this.getClass().getName, preUsage = Some("Version 0.0"))
  val help = parser.flag[Boolean](List("h", "help"), "print help")
  val srcO = parser.flag(List("sources", "read_sources"), "Set this flag to read the tree/src files. " +
    "Leave it off to read the tree/ files.")
  val muri_dirO = parser.option[File](List("muri_dir"),
    "LDMT_MURI_DIR", "The location of the ldmt-muri repo") {
    (s, opt) =>

      val file = new File(s)
      if (!file.exists)
        parser.usage("Muri directory \"" + s + "\" does not exist.")

      file
  }
  val languageO = parser.option[String]("lang", "LANG", "The language to generate a grammar for.") {
    (s, opt) =>
      if (!ALLOWED_LANGUAGES.contains(s)) {
        parser.usage("Allowed languages are " + ALLOWED_LANGUAGES)
      }
      s
  }
 
  val collectionsO = parser.multiOption[(String, String)]("coll", "COLLECTION ...", "One or more collections to examine." +
    " If this option is unspecified, all collections in the language will be used.") {
    (s, opt) =>
      try {
        val (lang :: coll :: Nil) = s.split(":").toList
        if (lang == "" || coll == "") {
          throw new MatchError()
        }
        (lang, coll)
      } catch {
        case e: MatchError => parser.usage("You must specify the collection by it's native language and name, as in 'kin:kgmc'. ")
      }
  }
  val outputFormatO = parser.option[String]("format","json or csv","the format to output grammars in. Default is json") {
    (s,opt) =>
      s.toLowerCase match {
        case "json" => "json"
        case "csv" => "csv"
        case _ => parser.usage("The format choices are 'json' and 'csv'.")
      }
  }
  val outputerFileO = parser.option[File](List("o","out"),"FILENAME","The file to write grammars to. If none is specified," +
    "stdout will be used.") {
    (s,opt)=>new File(s)
  }
  var log = new SimpleLogger(this.getClass().getName, SimpleLogger.WARN, new BufferedWriter(new OutputStreamWriter(System.err)))

  val getAllFiles: (File, String, String, List[String]) => List[File] =
    (root, lang, extension, searchList) => {
      assert(root.isDirectory)
      var result: List[File] = Nil
      for (child <- root.listFiles().sortBy(f => f.getName)) {
        if (child.isDirectory
          && !IGNORE_DIRS.contains(child.getName)
          && (searchList.contains(child.getName) || searchList.length == 0)) {
          result :::= getAllFiles(child, lang, extension, Nil)
        } else if (child.isFile && child.getName.endsWith(extension) && child.getName.contains(lang)) {
          result = child :: result
        }
      }
      result
    }


  /**
   * @return (X,Y): X is the non-terminal census and Y is the terminal census
   * <p>The non-terminal census looks like this:<br\>
   *   {"TAG" -> {["TAG1","TAG2"] -> 1} }<\br>
   *     where ["TAG1","TAG2"] is an observation of TAG's child nodes and 1 is the count
   *     <\p>
   *
   * <p>The terminal census looks like this:<br\>
   *   {"TAG" -> {"token" -> 1} }<\br>
   *     where "token" is an observation of TAG's child token and 1 is the count
   *     <\p>
   */
  val getTreeGrammar: (TreeNode, String) => (Grammar, Grammar) =
    (tree, source) => {
      // looks like type sugar doesn't extend to application
      var nonTerminals = NonTerminalGrammar()
      var terminals = TerminalGrammar()

      lazy val getTreeGrammarHelper: (TreeNode) => Unit =
        (tree) => {
          if (!tree.isToken) {
            if (tree.isTerminal) {
              assert(tree.getChildren.length == 1)
              val child = tree.getChildren(0).name
              terminals.addObservation(tree.name, child, 1, source)
            } else {
              val childList = tree.getChildren.map(child => child.name)
              nonTerminals.addObservation(tree.name, childList, 1, source)
            }
            for (child <- tree.getChildren) {
              getTreeGrammarHelper(child)
            }
          }
          ()
        }

      getTreeGrammarHelper(tree)
      (nonTerminals, terminals)
    }

  val buildGrammar: (List[File] => (Grammar, Grammar)) =
    (files) => {
      val nonTerminals = NonTerminalGrammar()
      val terminals = TerminalGrammar()
      for (file <- files) {
        val trees = MultiLineTreeParser(file.getAbsolutePath)
        for ((tree,index) <- trees.zipWithIndex) {
          log.debug(file.getName + " " + tree.getCanonicalString)
          val (tempNonTerm, tempTerm) = getTreeGrammar(tree,MultiLineTreeParser.getTreeId(file.getName,index+1))
          nonTerminals.foldIn(tempNonTerm)
          terminals.foldIn(tempTerm)
        }
      }
      (nonTerminals, terminals)
    }
  
  def doOutput(nonTerminal:Grammar,terminal:Grammar) {
    val strFunc:(Grammar)=>String = outputFormatO.value match {
      case Some("csv")  => (gram)=>gram.toCSVString
      case Some("json") => (gram)=>gram.toJSONString
      case _ => (gram)=>gram.toJSONString
    }
    val outputPrinter = outputerFileO.value match {
      case Some(file:File) => new PrintStream(file)
      case _ => System.out
    }
    outputPrinter.println("NonTerminals:")
    outputPrinter.println(strFunc(nonTerminal))
    outputPrinter.println("Terminals:")
    outputPrinter.println(strFunc(terminal))
    outputPrinter.flush()//not closing because closing stdout can kill the process
  }

  def main(args: Array[String]) {
    try {
      MultiLineTreeParser.log.logLevel = SimpleLogger.NONE
      parser.parse(args)
      if (help.value.isDefined) {
        parser.usage()
      }
      val language: String = languageO.value match {
        case Some(lang) => lang
        case None => parser.usage("You must specify a language from: " + ALLOWED_LANGUAGES)
      }
      val muri_dir: String = muri_dirO.value match {
        case Some(file: File) => file.getAbsolutePath
        case None => parser.usage("You must specify the location of the muri repo.")
      }

      val langsAndCollections = collectionsO.value.toList.groupBy {
        case (k, v) => k
      }.map {
        case (k, vs) => (k, vs.map {
          case (k, v) => v
        })
      }

      for ((collectionLang, collections) <- langsAndCollections) {
        val root = new File(
          (List(muri_dir, "data", "phase2", collectionLang, "tree") :::
            (if (srcO.value.isDefined) List("src") else List())).mkString(File.separator)
        )

        val treeFileList = getAllFiles(root, language, ".tree", collections).reverse

        val (nonTerminalGrammar, terminalGrammar) = buildGrammar(treeFileList)
        doOutput(nonTerminalGrammar, terminalGrammar)
      }

    } catch {
      case e: ArgotUsageException =>
        println(e.message)
    }
  }
}
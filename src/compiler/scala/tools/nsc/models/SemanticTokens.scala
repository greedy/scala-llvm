/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id$

package scala.tools.nsc.models;

import scala.tools.nsc.Global;
import scala.tools.nsc.symtab.{Flags,Names};
import scala.tools.nsc.util.{NameTransformer,Position,SourceFile};
import scala.collection.mutable.{HashMap,HashSet};


class SemanticTokens(val compiler: Global) {
  import compiler._;

  abstract class Kind {}
  object OBJECT  extends Kind;
  object CLASS   extends Kind;
  object TRAIT   extends Kind;
  object   DEF   extends Kind;
  object   VAL   extends Kind;
  object   VAR   extends Kind;
  object   ARG   extends Kind;
  object  TPARAM extends Kind;

  // static constants here

  abstract class Token {
    def length: Int;
    def prev: HasNext;
    def next: HasPrev; 
  }
  def eatKeyword(source : SourceFile, pos : Int, keywords : List[String]) : Int = {
    if (keywords.isEmpty) pos;
    else if (pos == source.content.length) Position.NOPOS;
    else if (source.beginsWith(pos, " "))
      eatKeywords(source, pos + 1);
    else if (source.beginsWith(pos, keywords.head + " "))
      eatKeywords(source, pos + keywords.head.length() + 1);
    else eatKeyword(source, pos, keywords.tail);
  }
  
  def eatKeywords(source : SourceFile, pos : Int) : Int = {
    val keywords = 
      "package" :: "val" :: "var" :: "def" :: "class" :: "override" :: "case" ::
      "object" :: "sealed" :: "private" :: "protected" :: Nil;
    if (pos != Position.NOPOS) eatKeyword(source, pos, keywords);
    else pos;      
  }

  trait HasNext extends Token {
    var next0: HasPrev = _;
    def next = next0;
  }

  trait HasPrev extends Token {
    var prev0: HasNext = _;
    def prev = prev0;
  }

  abstract class Actual extends HasNext with HasPrev {
    def convertToGap: Pair[Int, Actual]  = {
      val nextGap = next.isInstanceOf[Gap];
      val prevGap = prev.isInstanceOf[Gap];

      if (prevGap) {
        val ret = prev.length;
        val gap = prev.asInstanceOf[Gap];
        gap.setLength(gap.length + length);
        if (nextGap) {
          gap.setLength(gap.length + next.length);
          gap.next0 = next.next;
          next.next.prev0 = gap
        } else {
          gap.next0 = next;
          next.prev0 = gap
        }
        new Pair(ret, gap);
      }
      else if (nextGap) {
        val gap = next.asInstanceOf[Gap];
        gap.setLength(gap.length + length);
        gap.prev0 = prev;
        prev.next0 = gap;
        new Pair(0, gap)
      }
      else {
        prev.next0 = next;
        next.prev0 = prev;
        val gap = new Gap(prev);
        gap.setLength(length);
        new Pair(0, gap)
      }

    }
    def insert(prev1: HasNext) = {
      next0 = prev1.next;
      prev0 = prev1;
      prev0.next0 = this;
      next0.prev0 = this
    }

  } // Actual

  final class Gap extends Actual {
    def this(prev1: HasNext) = {
      this();
      insert(prev1);
    }
    override def toString() = "gap-" + length;

    var length0 : Int = -1;
    def length  : Int = length0;
    def setLength(length1: Int) = length0 = length1;

    // already gap
    override def convertToGap: Pair[Int,Actual] = new Pair(0, this);
  }

  def Process(unit : CompilationUnit) = new Process(unit);
  class Process(val unit : CompilationUnit) {
    private var doLog = true;
    def source = unit.source;

    def dbg(tree : Tree) = {(
        "TREE=" + tree + 
          (if (tree != null) (" CLASS=" + tree.getClass()) else "") + 
            " SYM=" + tree.symbol + 
              " POS=" + 
                (new Position(source, if (tree != null) tree.pos else -1)).dbgString
    )}

    val symbols = new HashMap[Symbol,Info];


    class Info(val symbol: Symbol) {
      var defined : Def = _;
      val uses = new HashSet[Use];
      symbols.update(symbol, this);
    }
    def info(symbol: Symbol): Info =
      if (symbols.contains(symbol)) symbols(symbol)
      else new Info(symbol);

    abstract class Semantic(val symbol: Symbol) extends Actual {
      val name = NameTransformer.decode(symbol.name.toString()).toString().trim();
      assert(symbol != NoSymbol);

      def tpe: Type = symbol.tpe;

      def length = name.length();
      def info: Info = if (symbols.contains(symbol)) symbols(symbol) else new Info(symbol);

      def kind = {
        val term0 = symbol;
        if (false) null
        else if (term0.isVariable)       VAR
        else if (term0.isValueParameter) ARG
        else if (term0.isMethod)         DEF
        else if (term0.isClass)          CLASS
        else if (term0.isModule)         OBJECT
        else if (term0.isValue)          VAL
        else if (term0.isTypeParameter)  TPARAM
        else if (term0.isType         )  TPARAM
        else {
          // System.err.println("UNRECOGNIZED SYMBOL: " + term0 + " " + name);
          null
        }
      }
    }

    class Def(symbol0: Symbol) extends Semantic(symbol0) {
      info.defined = this;
      override def toString() = "def-" + name + "-" + symbol.getClass();
    }
    class Use(symbol0: Symbol, tpe0: Type) extends Semantic(symbol0) {
      info.uses += this;

      override def tpe : Type = if (tpe0 != null) tpe0 else super.tpe;
      override def toString() = "use-" + name + "-" + symbol.getClass();
    }
    val list = new TokenList;

    build(unit.body);

    // ok start building....
    def build[T <: Tree](trees : List[T]) : Unit =
      for (val tree : T <- trees) build(tree);

    def build(tree0: Tree) : Unit = try {
      /* if (tree0.pos != Position.NOPOS) */ tree0 match {
      case tree : ImplDef => 
        val pos = eatKeywords(unit.source, tree.pos);
        if (pos == Position.NOPOS) {
          // inner types.
            // System.err.println("NOPOS: " + tree.getClass() + " " + (new Position(unit.source, tree.pos)).dbgString);
          //Thread.dumpStack();
        } else buildDef(tree.symbol, eatKeywords(unit.source, tree.pos));
        tree match {
        case cdef : ClassDef => build(cdef.tparams);
        case   _  => ; 
        }
        build(tree.impl.parents);
        build(tree.impl.body);
      case tree : ValOrDefDef => 
        if (!tree.symbol.hasFlag(Flags.ACCESSOR)) {
			  {
			    val pos = if (tree.name.toString().equals("<init>")) Position.NOPOS else 
            eatKeywords(unit.source, tree.pos);
          if (false) System.err.println("VALDEF: tree=" + tree + " sym=" + tree.symbol + " pos0=" + 
            tree.symbol.pos + " alias=" + tree.symbol.alias + " pos1=" +
            pos + " pos2=" + tree.pos + " " + unit.source.dbg(tree.pos) + " " + tree.symbol.hasFlag(Flags.SYNTHETIC));

			    if (pos != Position.NOPOS && !tree.hasFlag(Flags.SYNTHETIC)) 
			        buildDef(tree.symbol, pos);
			  }
			    
			  if (tree.isInstanceOf[DefDef]) {
			    val ddef = tree.asInstanceOf[DefDef];
			    build(ddef.tparams);
			    
			    for (val l0 <- ddef.vparamss; val arg <- l0) {
			      val pos0 = if (!unit.source.beginsWith(arg.pos, "val ")) arg.pos; 
											 else unit.source.skipWhitespace(arg.pos + ("val ").length());
			      buildDef(arg.symbol, pos0);
			      build(arg.tpt);
			    }
			  }
			  try {
          //TPT=scala.Iterator[DocGenerator.this.compiler0.CompilationUnit] 260 class scala.tools.nsc.ast.Trees$TypeTree scala.Iterator[DocGenerator.this.compiler0.CompilationUnit] class scala.tools.nsc.symtab.Types$$anon$5
          if (tree.tpt == null || tree.tpt.tpe == null) {
            //System.err.println("BAD: " + tree.tpt + " in " + tree); 
          } else {
            //System.err.println("TPT=" + tree.tpt + " " + tree.tpt.pos + " " + tree.tpt.getClass() + " " + tree.tpt.tpe + " " + tree.tpt.tpe.getClass() + " " + tree.tpt.tpe.getClass().getSuperclass());
  			    build(tree.tpt);
          }
			  } catch {
			    case e: Error =>
			      System.err.println("VALDEF: " + tree + " " + tree.tpt + " " + tree.pos + " " + tree.tpt.pos);
			      throw e;
			  }
        //System.err.println("RHS: " + tree.rhs + " " + tree.rhs.getClass() + " " + tree.rhs.getClass().getSuperclass());
			  build(tree.rhs);
			}
	  case tree : PackageDef => 
			//System.err.println("PACKAGE: " + tree.name);
			if (false) {
			  val pos = eatKeywords(unit.source, tree.pos);
		    if (pos != Position.NOPOS) 
			    buildDef(tree.symbol, pos);
			}
			build(tree.stats);
	  case tree : Function   => 
	    for (val arg <- tree.vparams) if (arg.pos != Position.NOPOS) { 
				  val name = arg.name.toString().trim();
				  val pos : Int = 
			    if (unit.source.beginsWith(arg.pos, "val ")) unit.source.skipWhitespace(arg.pos + ("val ").length());
			    else if (unit.source.content(arg.pos) == ':') {
			      var posx = arg.pos;
			      while (Character.isWhitespace(unit.source.content(posx - 1))) posx = posx - 1;
			      posx - name.length();
			    } else arg.pos;
				  buildDef(arg.symbol, pos);
				  build(arg.tpt);
				}
	    build(tree.body);
	  case tree : TypeTree =>
      val treex = tree;
	    val tree1 = if (tree.original != null) tree.original; else tree;
      def classes(clazz : java.lang.Class) : List[java.lang.Class] =
        if (clazz == null) Nil;
        else clazz :: classes(clazz.getSuperclass());
      if (tree.original == null) {
        if (false) System.err.println("NO_ORIGINAL: " + tree + " " + tree.tpe + " " + classes(tree.tpe.getClass()));
        
      }
      
	    if (tree.tpe != null) buildT(tree1, tree.tpe);
	    def buildT( tree : Tree, tpe : Type) : Unit = if (tree.pos != Position.NOPOS) tpe match {
		  case tpe0 : TypeRef => tree match {
		    case apt : AppliedTypeTree => 
		      buildUse(tpe.symbol, apt.tpt.pos, tpe0);
          //System.err.println("APT: " + treex + " vs. " + treex.original);
          //System.err.println("APT: " + treex.pos + " vs. " + treex.original.pos + " " + unit.source.dbg(treex.original.pos));
		      //System.err.println("APT: " + apt.tpt + " sym0=" + apt.tpt.symbol + " sym1=" + tpe0.sym + " apt.args=" + apt.args + " tpe0.args=" + tpe0.args);
	
		      buildTs (apt.args, tpe0.args);
		    case ident : Ident => buildUse(tpe0.sym, ident.pos, tpe0);
		    case select : Select =>
          if (select.symbol == NoSymbol) 
            if (false) System.err.println("BUILD_SELECT: " + select + " @ " + tpe0 + " SYM=" + select.symbol + " " + unit.source.dbg(select.pos));
		      try {
		        // build(select);
            buildUse(tpe0.symbol, selectPos(select), tpe0);
            //System.err.println("QUALIFIER: " + select.qualifier + " " + unit.source.dbg(select.qualifier.pos) + " " + tpe0.prefix + " " + tpe0.prefix.getClass() + " " + tpe0.prefix.getClass().getSuperclass() +" " + tpe0.prefix.widen + " " + tpe0.prefix.toLongString);            
						buildT(select.qualifier, tpe0.prefix);
          } catch {
						case e : Error => 
						  System.err.println("BUILD_SELECT: " + select + " @ " + tpe0 + " " + unit.source.dbg(select.pos));
						  throw e;
		      }
		    case tpt : TypeTree => 
          if (tpt.symbol != null) {
            System.err.println("SYM0 " + tpt.symbol + " " + unit.source.dbg(tpt.pos));
            buildUse(tpt.symbol, tpt.pos, tpe0);
          } else if (tpe0.symbol != null) {
            //System.err.println("TYPE_SYM1 " + tpe0.symbol + " " + unit.source.dbg(tpt.pos));
            buildUse(tpe0.symbol, tpt.pos, tpe0);
          } else {
            System.err.println("UNKNOWN TPT0: " + unit.source.dbg(tpt.pos) + " tpt=" + tpt + " " + tpt.symbol + " tpe0="+ tpe0 + " " + tpe0.symbol + " tpe0.args=" + tpe0.args);
          }
		    case sft : SelectFromTypeTree => 
		      build(sft.qualifier); // XXX: broken
			    if (false) System.err.println("SFTT: " + sft + " sym=" + sft.symbol + " selector=" + sft.selector + " qual=" + sft.qualifier + " qual.sym=" +
						  sft.qualifier.symbol + 
						  " qual.pos=" + unit.source.dbg(sft.qualifier.pos) + " symbol=" + sft.symbol + " type=" + tpe0 + 
						  " type.sym=" + tpe0.symbol);
		    case _ => System.err.println("UNKNOWN TPT2: " + tree + " vs. " + tpe0 + " " + tree.getClass() + " " + unit.source.content(tree.pos));
		  }
		  case tpe0 : MethodType => tree match {
		    case tpt: TypeTree =>
		      if (tpt.original != null) buildT(tpt.original, tpe);
		      else {
						System.err.println("UNKNOWN TPT3: " + tree + " vs. " + tpe0 + " " + unit.source.content(tree.pos));
		      }
		    case ident  : Ident  => buildT(ident,  tpe0.resultType);
		    case select : Select => buildT(select, tpe0.resultType);
		    case _ => System.err.println("UNKNOWN TPE: " + tree + " vs. " + tpe0 + " " + tree.getClass());
		  }
		  case tpe0 : RefinedType => tree match {
			  case cpt : CompoundTypeTree => 
			  	buildTs(cpt.templ.parents, tpe0.parents);
        
			  case _ : TypeTree => 
			    // System.err.println("UNKNOWN TPE13: " + dbg(tree) + " tpe0=" + tpe0 + " " + tpe0.parents);
			  case _ =>
				  if (false) System.err.println("UNKNOWN TPE5: " + dbg(tree) + " tpe0=" + tpe0 + " " + tpe0.parents);
		  }
		  case tpe0 : ThisType => tree match {
  		  case stt : SingletonTypeTree => stt.ref match {
  			  case ths : This => build(ths);
          case _ => System.err.println("UNKNOWN TPE11: " + tpe0 + " " + stt + " " + stt.ref + " " + stt.ref.getClass() + " " + unit.source.dbg(tree.pos));
        }
        case tt : This => 
        case _ : Ident =>
        case _ : Select =>
        case tt : TypeTree => 
          if (false) System.err.println("UNKNOWN TPE12: " + tpe0 + " " + tree + " " + tree.getClass() + " " + unit.source.dbg(tree.pos));
        case _ => 
          if (false) System.err.println("UNKNOWN TPE10: " + tpe0 + " " + tree + " " + tree.getClass() + " " + unit.source.dbg(tree.pos));
		  }
		  case tpe0 : SingleType => tree match {
        case ident  : Ident  => buildUse(tpe0.sym, ident.pos, tpe0);
        case select : Select =>
        buildUse(tpe0.symbol, selectPos(select), tpe0);
        //System.err.println("QUALIFIER-0: " + select.qualifier + " " + unit.source.dbg(select.qualifier.pos) + " " + tpe0.prefix + " " + tpe0.prefix.getClass() + " " + tpe0.prefix.getClass().getSuperclass() +" " + tpe0.prefix.widen + " " + tpe0.prefix.toLongString);            
        buildT(select.qualifier, tpe0.prefix);

        case _ =>
          if (false) System.err.println("UNKNOWN TPE8: " + tree + " " + unit.source.dbg(tree.pos) + " TPE=" + tpe0 + " PRE=" + tpe0.pre + " SYM=" + tpe0.sym);
			  
		  }
      case ctype : ConstantType =>
        if (false) System.err.println("UNKNOWN CONSTANT_TYPE: " + tree + " " + ctype + " " + unit.source.dbg(tree.pos));
        
		  case ErrorType => 
		  case _ => {
        if (false) System.err.println("UNKNOWN TPE4: " + tree + " " + tpe + " " + tpe.getClass() + " " + unit.source.dbg(tree.pos));
		  }
		};
    def buildTs(trees : List[Tree], types : List[Type]): Unit = if (!trees.isEmpty && !types.isEmpty) {
			  buildT (trees.head, types.head);
			  buildTs(trees.tail, types.tail);
		} else if (trees.isEmpty != types.isEmpty) {
 			if (false && doLog) {
        Console.println("" + treex + " vs. " + treex.original);
        if (treex.original != null) 
          Console.println("" + treex.tpe + " vs. " + treex.original.tpe);
 			  logError("Tree vs. Type mismatch: " + trees + " " + types + " " + unit.source.dbg(tree.pos), null);
        doLog = false;
      }
    };
    
	  case tree : AbsTypeDef =>
    	//System.err.println("ABS: " + tree.symbol + " " + unit.source.dbg(tree.namePos) + " " + unit.source.dbg(tree.pos));
	    buildDef(tree.symbol, tree.namePos);
	    buildDef(tree.symbol, tree.pos);
	    build(tree.lo);
      build(tree.hi);
	  case tree : Bind =>       buildDef(tree.symbol, tree.pos);
	                            build(tree.body);
	  case tree : Ident      => buildUse(tree.symbol, tree.pos, tree.tpe);
	  case tree : Select     => 
			try {
      	build(tree.qualifier); 
			} catch {
			  case e : Error => System.err.println("SELECTQ: " + tree + " " + tree.qualifier + " " + unit.source.dbg(tree.qualifier.pos)); throw e;
			}
			try {
			  if (tree.pos >= unit.source.content.length) {
			    if (false) System.err.println("BAD_SELECT_QUALIFIER " + tree + " @ " + unit.source.dbg(tree.pos));
          
        } else {
          //System.err.println("SELECT-0: " + tree.symbol + " " + unit.source.dbg(tree.pos) + " " + (tree.pos - selectPos(tree)));
					buildUse(tree.symbol, selectPos(tree), tree.tpe);
			  }
			} catch {
			  case e : Error => System.err.println("SELECTU: " + tree + " " + tree.symbol + " " + unit.source.dbg(tree.pos)); throw e;
			}
    case tree : TypeApply => 
		  //System.err.println("TYPE_APPLY: " + tree + " " + unit.source.dbg(tree.pos));    
      if (!tree.args0.isEmpty) {
        //System.err.println("ARGS: " + unit.source.dbg(tree.args0.head.pos));    
      }
      build(tree.fun0); 
      build(tree.args0);
    case tree : Apply => 
      //System.err.println("NORM_APPLY: " + tree + " " + unit.source.dbg(tree.pos));    
      build(tree.fun0); 
      build(tree.args0);
	  case tree : GenericApply => 
	    //System.err.println("GEN_APPLY: " + tree + " " + unit.source.dbg(tree.pos));    
  	  build(tree.fun0); 
			build(tree.args0);
	  case tree : Typed => build(tree.expr); build(tree.tpt);
	  case tree : Block => 
    	if (false) {
				if (!tree.stats.isEmpty)
          System.err.println("BLOCKS: " + tree.stats.head + " " + tree.stats.head.getClass());
        System.err.println("BLOCKE: " + tree.expr + " " + tree.expr.getClass());
      }
	    build(tree.stats); build(tree.expr);
	  case tree : CaseDef    => 
	    build(tree.pat); build(tree.guard); build(tree.body);
	  case tree : Sequence   => build(tree.trees);
	  case tree : Assign     => build(tree.lhs); build(tree.rhs);
	  case tree : If         => build(tree.cond); build(tree.thenp); build(tree.elsep);
	  case tree : New        => 
    	//System.err.println("NEW: " + tree.tpt + " " + tree.tpt.getClass());
      build(tree.tpt);
	  case tree : Match      => build(tree.selector); build(tree.cases);
	  case tree : Return     => build(tree.expr);
	  case tree : LabelDef   => build(tree.rhs);
	  case tree : Throw      => build(tree.expr);
	  case tree : Try        => build(tree.block); build(tree.catches); build(tree.finalizer);
	  case tree : Alternative => build(tree.trees);
	  case tree : This    => 
    	//System.err.println("THIS: " + tree.symbol + " " + tree.qual + " " + unit.source.dbg(tree.pos) + " " + tree.tpe);
	    if (tree.symbol != null) buildUse(tree.symbol, tree.pos, tree.tpe);
	    //Thread.dumpStack();
	  case tree : AliasTypeDef => 
	    System.err.println("ALIAS: " + tree);
	    build(tree.rhs); build(tree.tparams); buildDef(tree.symbol, tree.pos);
    case tree : DocDef     => build(tree.definition);
    case tree : Attributed => build(tree.definition);
	  case tree : Import     => build(tree.expr);
    case tree : AppliedTypeTree => ;
    case tree : SingletonTypeTree => ;
    case tree : Super   => ;
    case tree : Literal => ;
	  case EmptyTree => ;
	  case _ => ;
	    System.err.println("BAIL: " + unit.source.dbg(tree0.pos) + " " + tree0 + " " + tree0.getClass());
    } 
  } catch {
    case t : Throwable => logError("Error occured at " + unit.source.dbg(tree0.pos), t);
  }

  def buildUse(term: Symbol, pos: Int, tpe: Type) = buildSym(term, pos, false, tpe);
  def buildDef(term: Symbol, pos: Int) = buildSym(term, pos, true, null);

  def buildSym(term: Symbol, pos: Int, isDef: Boolean, tpe: Type) : Unit = 
    if (term.hasFlag(Flags.ACCESSOR)) buildSym(term.accessed, pos, isDef, tpe); 
    else if (pos == Position.NOPOS) {
      System.err.println("NOPOS: " + term);
      Thread.dumpStack();
    }
    else if (term != NoSymbol) {
      val name = NameTransformer.decode(term.name.toString()).toString().trim();
      val buf = unit.source.content;
      val cs = name.toChars;
      var idx = 0;
      if (cs.length + pos > buf.length) return;
      else while (idx < cs.length) {
        if (buf(pos + idx) != cs(idx)) {
          //System.err.println("MISMATCH: " + name + "[" + idx + "] " + unit.source.dbg(pos));
          //Thread.dumpStack();
          return;
        }
        else idx = idx + 1;
      }
      if (cs.length + pos + 1 < buf.length) {
        if (Character.isJavaIdentifierPart(buf(pos + cs.length))) {
          //System.err.println("MISMATCH: " + name + "[last] " + unit.source.dbg(pos));
          return;
        }
      }
      try {
        list.put(pos, (if (isDef) new Def(term) else new Use(term, tpe)));
      } catch {
        case e : Error => e.printStackTrace();
      }
    }

  def selectPos(tree : Select): Int = if (tree.pos == Position.NOPOS) Position.NOPOS else {
    val buf = unit.source.content;
    if (tree.pos >= buf.length) {
      if (false) {
        System.err.println("" + tree + "@" + tree.pos + " not in " + unit.source.file.getName() + "[" + buf.length + "]");
        Thread.dumpStack();
        throw new Error();
      } 
      return 0;
    }

    val pos = 
      if (buf(tree.pos) != '.') tree.pos;
      else {
        def f(x : Int) : Int = {
          if (Character.isWhitespace(buf(x))) f(x + 1);
          else x;
        }
        f(tree.pos + 1);
      }
    pos;
  };

  class TokenList {
    object begin extends HasNext {
        def prev = this;
        def length = 0;
    }
    object end extends HasPrev {
        def next = this;
        def length = 0;
    }
    // initialize
    begin.next0 = end;
    end.prev0 = begin;

    def tokenAt(offset: Int) = {
      cursor.seek(offset);
      if (cursor.token.isInstanceOf[Semantic]) cursor.token.asInstanceOf[Semantic];
      else null;
    }

    def put(offset: Int, tok: Actual): Unit = tok match {
      case tok0 : Semantic => put(offset, tok0)
      case gap  : Gap      => 
    }

    def put(offset: Int, tok: Semantic): Unit = {
      cursor.seek(offset);
      if (cursor.token == end) {
        assert(offset >= cursor.offset);
        if (offset > cursor.offset) {
          // add a gap.
          val gap = new Gap(end.prev);
          gap.setLength(offset - cursor.offset);
          cursor.offset = offset;
        }
        // append.
        tok.insert(end.prev);
        cursor.offset = cursor.offset + tok.length;
      } else if (!cursor.token.isInstanceOf[Gap]) {
        val sem = cursor.token.asInstanceOf[Semantic];
        if (sem.symbol == tok.symbol) return;
        if (sem.symbol != tok.symbol &&
            sem.symbol.getClass() == tok.symbol.getClass() &&
            sem.symbol.pos == tok.symbol.pos) return;

        if (false) {
          System.err.println("NOT_GAP: " + sem.symbol + " " + sem.symbol.getClass() + " " + unit.source.dbg(sem.symbol.pos) + " " + sem.symbol.flags);
          System.err.println("NOT_GAP: " + tok.symbol + " " + tok.symbol.getClass() + " " + unit.source.dbg(tok.symbol.pos) + " " + tok.symbol.flags);
          System.err.println("LIST: " + this);
          System.err.println("POS: " + unit.source.dbg(offset));
  
          Thread.dumpStack();
          throw new Error();
        }
      } else {
        val gap = cursor.token.asInstanceOf[Gap];
        if (!(offset - cursor.offset + tok.length <= gap.length)) {
          System.err.println("LIST  =" + this);
          System.err.println("OFFSET=" + offset + " " + tok + " " + tok.length);
          System.err.println("       " + cursor.offset + " " + gap.length);
          throw new Error();
        }
        if (offset == cursor.offset) {
          // replace or prepend
          tok.prev0 = gap.prev0;
          if (tok.length == gap.length) { // replace gap
            tok.next0 = gap.next0;
          } else {
            gap.setLength(gap.length - tok.length);
            tok.next0 = gap;
          }
          tok.next0.prev0 = tok;
          tok.prev0.next0 = tok;
          cursor.token = tok;
        } else {
          // append
          val diff = (cursor.offset + gap.length) - (offset + tok.length);

          gap.setLength(gap.length - tok.length - diff);
          tok.prev0 = gap;
          tok.next0 = gap.next;
          tok.next0.prev0 = tok;
          tok.prev0.next0 = tok;
          if (diff != 0) {
            val gap0 = new Gap(tok);
            gap0.setLength(diff);
          }
        }
      }
    }

    override def toString() = {
      var node = begin.next;
      var str = "";
      while (node != end) {
        str = str + " " + node;
        node = node.next;
      }
      str
    }

    object cursor {
      var token  : Token = end;
      var offset : Int   = 0;

      def next : Unit = if (token == end) end else {
        offset = offset + token.length;
        token  = token.next;
      }
      def prev : Unit = if (token.prev == begin) token else {
        offset = offset - token.prev.length;
        token = token.prev;
      }
      def seek(soffset : Int) : Unit = if (soffset == 0) {
        token = begin.next;
        offset = 0;
      } else {
        assert(soffset > 0);
        while (offset                >  soffset) prev;
        while (offset + token.length <= soffset && token != end) {
          val len0 = offset;
          next;
        }
      }
      def convertToGap = if (token.isInstanceOf[Actual]) {
        val ret = token.asInstanceOf[Actual].convertToGap;
        offset  = offset - ret._1;
        token   = ret._2;
      }
    }

    // add or delete characters
    def adjust(offset: Int, /* where */
               length: Int, /* how many characters are modified */
               to    : Int  /* length of new string */) = {
      cursor.seek(offset);
      if (cursor.token != end) {
        cursor.convertToGap;
        while (cursor.offset + cursor.token.length < offset + length && cursor.token.next != end) {
          val save = cursor.offset;
          cursor.next;
            cursor.convertToGap;
          assert(cursor.offset == save);
        }
         if (length != to && cursor.token != end) {
           val diff = to - length;
           val gap = cursor.token.asInstanceOf[Gap];
           gap.setLength(gap.length + diff);
         };
      }
    }

  } // TokenList

  }
}


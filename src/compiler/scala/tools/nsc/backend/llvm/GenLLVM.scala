/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author  Geoffrey Reedy
 */

package scala.tools.nsc
package backend.llvm

import scala.collection._

import ch.epfl.lamp.llvm.{Instruction => LMInstruction, Constant => LMConstant, Block => LMBlock, _}

import io.AbstractFile
import java.io.OutputStreamWriter

abstract class GenLLVM extends SubComponent {
  import global._
  import icodes._
  import icodes.opcodes._

  val phaseName = "llvm"

  override def newPhase(p: Phase) = new LLVMPhase(p)

  class LLVMPhase(prev: Phase) extends ICodePhase(prev) {
    def name = phaseName

    override def run {
      classes.values foreach apply
    }

    object moduleGenerator extends ModuleGenerator

    override def apply(cls: IClass) {
      moduleGenerator.genClass(cls)
    }
  }


  class ModuleGenerator {

    object Runtime {
      val rtClass: LMStructure with AliasedType = new LMStructure(Seq(LMInt.i8.pointer, LMInt.i32, rtClass.pointer, LMInt.i32, new LMArray(0, rtTraitInfo))).aliased(".class")
      val rtTraitInfo = new LMStructure(Seq(rtTrait.pointer, LMInt.i32))
      val rtTrait = new LMStructure(Seq(LMInt.i8.pointer)).aliased(".trait")
      val rtObject = new LMStructure(Seq(rtClass.pointer)).aliased(".object")
      val rtDispatch = new LMStructure(Seq(rtClass.pointer, LMInt.i8.pointer)).aliased(".dispatch")
      val rtNew = new LMFunction(rtObject.pointer, ".rt.new", Seq(ArgSpec(new LocalVariable("cls", rtClass.pointer))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtGetClass = new LMFunction(rtClass.pointer, ".rt.get_class", Seq(ArgSpec(new LocalVariable("object", rtObject.pointer))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtLookupMethod = new LMFunction(LMInt.i8.pointer, ".rt.lookup_method", Seq(ArgSpec(new LocalVariable("dtable", rtDispatch.pointer)), ArgSpec(new LocalVariable("cls", rtClass.pointer))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxi1 = new LMFunction(symType(definitions.BoxedBooleanClass), ".rt.box.i1", Seq(ArgSpec(new LocalVariable("v", LMInt.i1))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxi8 = new LMFunction(symType(definitions.BoxedByteClass), ".rt.box.i8", Seq(ArgSpec(new LocalVariable("v", LMInt.i8))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxi16 = new LMFunction(symType(definitions.BoxedShortClass), ".rt.box.i16", Seq(ArgSpec(new LocalVariable("v", LMInt.i16))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxi32 = new LMFunction(symType(definitions.BoxedIntClass), ".rt.box.i32", Seq(ArgSpec(new LocalVariable("v", LMInt.i32))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxi64 = new LMFunction(symType(definitions.BoxedLongClass), ".rt.box.i64", Seq(ArgSpec(new LocalVariable("v", LMInt.i64))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxFloat = new LMFunction(symType(definitions.BoxedFloatClass), ".rt.box.float", Seq(ArgSpec(new LocalVariable("v", LMFloat))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtBoxDouble = new LMFunction(symType(definitions.BoxedDoubleClass), ".rt.box.double", Seq(ArgSpec(new LocalVariable("v", LMDouble))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxi1 = new LMFunction(LMInt.i1, ".rt.unbox.i1", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedBooleanClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxi8 = new LMFunction(LMInt.i8, ".rt.unbox.i8", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedByteClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxi16 = new LMFunction(LMInt.i16, ".rt.unbox.i16", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedShortClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxi32 = new LMFunction(LMInt.i32, ".rt.unbox.i32", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedIntClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxi64 = new LMFunction(LMInt.i64, ".rt.unbox.i64", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedLongClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxFloat = new LMFunction(LMFloat, ".rt.unbox.float", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedFloatClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtUnboxDouble = new LMFunction(LMDouble, ".rt.unbox.double", Seq(ArgSpec(new LocalVariable("v", symType(definitions.BoxedDoubleClass)))), false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
      val rtHeader = Seq(
        new TypeAlias(rtClass),
        new TypeAlias(rtTrait),
        new TypeAlias(rtDispatch),
        new TypeAlias(rtObject.aliased("java.lang.Boolean")),
        new TypeAlias(rtObject.aliased("java.lang.Byte")),
        new TypeAlias(rtObject.aliased("java.lang.Short")),
        new TypeAlias(rtObject.aliased("java.lang.Integer")),
        new TypeAlias(rtObject.aliased("java.lang.Long")),
        new TypeAlias(rtObject.aliased("java.lang.Float")),
        new TypeAlias(rtObject.aliased("java.lang.Double")),
        rtLookupMethod.declare,
        rtNew.declare,
        rtGetClass.declare,
        rtLookupMethod.declare,
        rtBoxi1.declare,
        rtBoxi8.declare,
        rtBoxi16.declare,
        rtBoxi32.declare,
        rtBoxi64.declare,
        rtBoxFloat.declare,
        rtBoxDouble.declare,
        rtUnboxi1.declare,
        rtUnboxi8.declare,
        rtUnboxi16.declare,
        rtUnboxi32.declare,
        rtUnboxi64.declare,
        rtUnboxFloat.declare,
        rtUnboxDouble.declare
      )
    }

    import Runtime._

    def isMain(c: IClass) = c.symbol.isClassOfModule && c.symbol.fullName('.') == settings.Xmainclass.value

    def genClass(c: IClass) {
      println(c)
      println(c.symbol.isClassOfModule)
      println(c.symbol.fullName('.'))
      println(settings.Xmainclass.value)
      println(isMain(c))
      val externFuns: mutable.Map[Symbol,LMFunction] = new mutable.HashMap
      val externClasses: mutable.Map[Symbol,LMGlobalVariable[_<:ConcreteType]] = new mutable.HashMap
      val internalFuns: mutable.Set[Symbol] = new mutable.HashSet
      val internalClasses: mutable.Set[Symbol] = new mutable.HashSet
      val extraDefs: mutable.ListBuffer[ModuleComp] = new mutable.ListBuffer
      val externModules: mutable.Map[Symbol,LMGlobalVariable[_<:ConcreteType]] = new mutable.HashMap
      val externTypes: mutable.ListBuffer[AliasedType] = new mutable.ListBuffer

      def generateMain(f: LMFunction) = {
        val mainfun = new LMFunction(LMInt.i32, "main", Seq(ArgSpec(new LocalVariable("argc", LMInt.i32)), ArgSpec(new LocalVariable("argv", LMInt.i8.pointer.pointer))), false, Externally_visible, Default, Ccc, Seq.empty, Seq.empty, None, None, None)
        val asobject = new LocalVariable("instance.o", rtObject.pointer)
        val asinstance = new LocalVariable("instance", symType(c.symbol))
        mainfun.define(Seq(
          LMBlock(None, Seq(
            new call_void(f, Seq(new CGlobalAddress(externModule(c.symbol)))),
            new ret(new CInt(LMInt.i32, 0))))))
      }

      def recordType(t: LMType) {
        t match {
          case a:AliasedType => externTypes += a
          case p:LMPointer => recordType(p.target)
          case f:LMFunctionType => { recordType(f.returnType); f.argTypes.foreach(recordType) }
          case _ => ()
        }
      }

      def externModule(s: Symbol) = {
        recordType(symType(s))
        if (c.symbol == s) {
            new LMGlobalVariable(moduleInstanceName(s), symType(s).asInstanceOf[LMPointer].target, Externally_visible, Default, false)
        } else {
          externModules.getOrElseUpdate(s, {
            new LMGlobalVariable(moduleInstanceName(s), symType(s).asInstanceOf[LMPointer].target, Externally_visible, Default, true)
          })
        }
      }

      def externFun(s: Symbol) = {
        externFuns.getOrElseUpdate(s, {
          val funtype = symType(s).asInstanceOf[LMFunctionType]
          funtype.argTypes.foreach(recordType)
          recordType(funtype.returnType)
          val args = funtype.argTypes.zipWithIndex.map{case (t,i) => ArgSpec(new LocalVariable("arg"+i, t), Seq.empty)}
          new LMFunction(funtype.returnType, llvmName(s), args, false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
        })
      }

      def externClass(s: Symbol) = {
        if (c.symbol == s) {
          new LMGlobalVariable(classInfoName(s), rtClass, Externally_visible, Default, true)
        } else {
          externClasses.getOrElseUpdate(s, {
            new LMGlobalVariable(classInfoName(s), rtClass, Externally_visible, Default, true)
          })
        }
      }

      val classInfo: Seq[ModuleComp] = {
        val ct = classType(c)
        val supers = Stream.iterate(c.symbol.superClass)(_.superClass).takeWhile(s => s != NoSymbol)
        supers.map(classType).foreach(recordType)
        recordType(ct)
        val n = new CArray(LMInt.i8, (llvmName(c.symbol)+"\0").map(_.toByte).map(new CInt(LMInt.i8, _)))
        val ng = new LMGlobalVariable(".classname", n.tpe, Private, Default, true)
        val ci = new CStruct(Seq(new Cgetelementptr(ng, Seq[LMConstant[LMInt]](0,0), LMInt.i8.pointer),
                                 new Cptrtoint(new Cgetelementptr(new CNull(ct.pointer), Seq[LMConstant[LMInt]](1), ct.pointer), LMInt.i32),
                                 new CGlobalAddress(externClass(c.symbol.superClass)),
                                 new CInt(LMInt.i32, 0),
                                 new CArray(rtTraitInfo, Seq.empty)))
        val cig = new LMGlobalVariable[LMStructure](classInfoName(c.symbol), rtClass, Externally_visible, Default, true)
        Seq(cig.define(ci), ng.define(n))
      }

      val moduleInfo: Seq[ModuleComp] = {
        if (c.symbol.isClassOfModule) {
          val ig = new LMGlobalVariable(moduleInstanceName(c.symbol), symType(c.symbol).asInstanceOf[LMPointer].target, Externally_visible, Default, false)
          val initFun = new LMFunction(LMVoid, ".init."+moduleInstanceName(c.symbol), Seq.empty, false, Internal, Default, Ccc, Seq.empty, Seq.empty, None, None, None)
          val asobject = new LocalVariable("object", rtObject.pointer)
          val asinstance = new LocalVariable("instance", symType(c.symbol).asInstanceOf[LMPointer])
          val asvalue = new LocalVariable("value", symType(c.symbol).asInstanceOf[LMPointer].target)
          val initFundef = initFun.define(Seq(
            LMBlock(None, Seq(
              new call(asobject, rtNew, Seq(new CGlobalAddress(externClass(c.symbol)))),
              new bitcast(asinstance, asobject),
              new call_void(externFun(c.lookupMethod("<init>").get.symbol), Seq(asinstance)),
              new load(asvalue, asinstance),
              new store(asvalue, new CGlobalAddress(ig)),
              retvoid
            ))))
          val ctors = new LMGlobalVariable("llvm.global_ctors", new LMArray(1, new LMStructure(Seq(LMInt.i32, new LMFunctionType(LMVoid, Seq.empty, false).pointer))), Appending, Default, false)
          val ctorsdef = ctors.define(new CArray(ctors.tpe.elementtype, Seq(new CStruct(Seq(new CInt(LMInt.i32, 1), new CFunctionAddress(initFun))))))
          Seq(ig.define(new CUndef(ig.tpe)),initFundef,ctorsdef)
        } else {
          Seq.empty
        }
      }

      def genFun(m: IMethod) = {
        internalFuns += m.symbol
        //m.dump
        val thisarg = ArgSpec(new LocalVariable(".this", symType(c.symbol)))
        val args = thisarg +: m.params.map(p => ArgSpec(new LocalVariable(llvmName(p.sym), localType(p))))
        val fun = new LMFunction(typeType(m.returnType.toType), llvmName(m.symbol), args, false, Externally_visible, Default, Fastcc, Seq.empty, Seq.empty, None, None, None)
        recordType(fun.tpe)
        fun.args.map(_.lmvar.tpe).foreach(recordType)
        if (isMain(c) && m.params.isEmpty && m.symbol.simpleName.toString().trim() == "main" && fun.resultType == LMVoid) {
          extraDefs += generateMain(fun)
        }
        val blocks: mutable.ListBuffer[LMBlock] = new mutable.ListBuffer
        var varidx = 0
        val locals: mutable.Map[Symbol,LMValue[_<:ConcreteType]] = mutable.Map()
        def nextvar[T <: ConcreteType](t: T) = {
          val v = new LocalVariable(varidx.toString, t)
          varidx = varidx + 1
          v
        }
        m.code.blocks.foreach { bb =>
          val stack: mutable.Stack[(LMValue[_<:ConcreteType],Symbol)] = mutable.Stack()
          def popn(n: Int) {
            for (_ <- 0 until n) stack.pop
          }
          val insns: mutable.ListBuffer[LMInstruction] = new mutable.ListBuffer
          def upcast(src: LMValue[LMPointer], srcsym: Symbol, targetsym: Symbol): LMValue[LMPointer] = {
            recordType(symType(targetsym))
            val v = nextvar(symType(targetsym).asInstanceOf[LMPointer])
            insns.append(new bitcast(v, src))
            v
          }
          val (consumedTypes,producedTypes) = stackUsage(bb)
          consumedTypes.zipWithIndex.foreach { case (sym,n) =>
            val tpe = symType(sym)
            recordType(tpe)
            val reg = new LocalVariable(blockName(bb)+".in."+n.toString,tpe)
            val sources = bb.predecessors.map(pred => (blockLabel(pred), new LocalVariable(blockName(pred)+".out."+n.toString,tpe)))
            insns.append(new phi(reg, sources))
            stack.push((reg, sym))
          }
          bb.foreach { i =>
            i match {
              case THIS(clasz) => {
                stack.push((thisarg.lmvar,clasz))
              }
              case CONSTANT(const) => {
                val value = constValue(const)
                stack.push((value,const.tpe.typeSymbol))
              }
              case LOAD_ARRAY_ITEM(kind) => println("unhandled " + i)
              case LOAD_LOCAL(local) => {
                stack.push((locals(local.sym), local.sym.tpe.typeSymbol))
              }
              case LOAD_FIELD(field, isStatic) => {
                val v = nextvar(symType(field))
                val fieldptr = nextvar(v.tpe.pointer)
                val fieldidx = classes(field.owner).fields.indexWhere(f => f.symbol == field)
                val (ivar,isym) = stack.pop
                val instance = upcast(ivar.asInstanceOf[LMValue[LMPointer]],isym,field.owner)
                insns.append(new getelementptr(fieldptr, instance.asInstanceOf[LMValue[LMPointer]], Seq(new CInt(LMInt.i8,0),new CInt(LMInt.i32,fieldidx+1))))
                insns.append(new load(v, fieldptr))
                stack.push((v,field.tpe.typeSymbol))
              }
              case LOAD_MODULE(module) => {
                stack.push((new CGlobalAddress(externModule(module)), module))
              }
              case STORE_ARRAY_ITEM(kind) => println("unhandled " + i)
              case STORE_LOCAL(local) => {
                locals(local.sym) = stack.pop._1
              }
              case STORE_FIELD(field, isStatic) => {
                val v = nextvar(symType(field))
                val fieldptr = nextvar(v.tpe.pointer)
                val fieldidx = classes(field.owner).fields.indexWhere(f => f.symbol == field)
                val value = stack.pop._1
                val instance = stack.pop._1
                insns.append(new getelementptr(fieldptr, instance.asInstanceOf[LMValue[LMPointer]], Seq(new CInt(LMInt.i8,0),new CInt(LMInt.i32,fieldidx+1))))
                insns.append(new store(value, fieldptr))
              }
              case CALL_PRIMITIVE(primitive) => {
                primitive match {
                  case Negation(_) => {
                    val (arg,argsym) = stack.pop
                    val v = nextvar(arg.tpe)
                    arg.tpe match {
                      case i:LMInt => insns.append(new mul(v.asInstanceOf[LocalVariable[LMInt]], arg.asInstanceOf[LMValue[LMInt]], new CInt(i,-1)))
                      case f:LMFloat => insns.append(new fmul(v.asInstanceOf[LocalVariable[LMFloat]], arg.asInstanceOf[LMValue[LMFloat]], new CFloat(-1)))
                      case d:LMDouble => insns.append(new fmul(v.asInstanceOf[LocalVariable[LMDouble]], arg.asInstanceOf[LMValue[LMDouble]], new CDouble(-1)))
                    }
                    stack.push((v,argsym))
                  }
                  case Test(op, _, z) => {
                    val arg = stack.pop._1
                    val cmpto = if (z) new CZeroInit(arg.tpe) else stack.pop._1
                    val result = nextvar(LMInt.i1)
                    def cmp(intop: ICond, floatop: FCond) = {
                      arg.tpe match {
                        case _:LMInt => insns.append(new icmp(result, intop, arg.asInstanceOf[LMValue[LMInt]], cmpto.asInstanceOf[LMValue[LMInt]]))
                        case _:LMFloat => insns.append(new fcmp(result, floatop, arg.asInstanceOf[LMValue[LMFloat]], cmpto.asInstanceOf[LMValue[LMFloat]]))
                        case _:LMDouble => insns.append(new fcmp(result, floatop, arg.asInstanceOf[LMValue[LMDouble]], cmpto.asInstanceOf[LMValue[LMFloat]]))
                        case _:LMPointer => insns.append(new icmp_p(result, intop, arg.asInstanceOf[LMValue[LMPointer]], cmpto.asInstanceOf[LMValue[LMPointer]]))
                      }
                    }
                    val cond = op match {
                      case EQ => cmp(ICond.ieq, FCond.oeq)
                      case NE => cmp(ICond.ine, FCond.one)
                      case LT => cmp(ICond.slt, FCond.olt)
                      case GE => cmp(ICond.sge, FCond.oge)
                      case LE => cmp(ICond.sle, FCond.ole)
                      case GT => cmp(ICond.sgt, FCond.ogt)
                    }
                    stack.push((result, definitions.BooleanClass))
                  }
                  case Comparison(op, _) => {
                    val v2 = stack.pop._1
                    val v1 = stack.pop._1
                    val result = nextvar(LMInt.i32)
                    v1.tpe match {
                      case _:LMFloatingPointType => {
                        val v1greater = nextvar(LMInt.i1)
                        val equal = nextvar(LMInt.i1)
                        val unordered = nextvar(LMInt.i1)
                        val unorderedval = op match {
                          case CMPL => new CInt(LMInt.i32, -1)
                          case CMPG => new CInt(LMInt.i32, 1)
                          case CMP => new CInt(LMInt.i32, 1)
                        }
                        insns.append(new fcmp(equal, FCond.oeq, v1.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]], v2.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]]))
                        insns.append(new fcmp(v1greater, FCond.ogt, v1.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]], v2.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]]))
                        insns.append(new fcmp(unordered, FCond.uno, v1.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]], v2.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]]))
                        val temp1 = nextvar(LMInt.i32)
                        val temp2 = nextvar(LMInt.i32)
                        insns.append(new select(temp1, v1greater, new CInt(LMInt.i32, 1), new CInt(LMInt.i32, -1)))
                        insns.append(new select(temp2, equal, new CInt(LMInt.i32, 0), temp1))
                        insns.append(new select(result, unordered, unorderedval, temp2))
                      }
                      case it:LMInt => {
                        val v1greater = nextvar(LMInt.i1)
                        val equal = nextvar(LMInt.i1)
                        insns.append(new icmp(equal, ICond.ieq, v1.asInstanceOf[LMValue[LMInt]], v2.asInstanceOf[LMValue[LMInt]]))
                        insns.append(new icmp(v1greater, ICond.sgt, v1.asInstanceOf[LMValue[LMInt]], v2.asInstanceOf[LMValue[LMInt]]))
                        val temp = nextvar(LMInt.i32)
                        insns.append(new select(temp, v1greater, new CInt(LMInt.i32, 1), new CInt(LMInt.i32, -1)))
                        insns.append(new select(result, equal, new CInt(LMInt.i32, 0), temp))
                      }
                    }
                    stack.push((result, definitions.IntClass))
                  }
                  case Arithmetic(NOT, _) => {
                    val (argt, s) = stack.pop
                    val arg = argt.asInstanceOf[LMValue[LMInt]]
                    val res = nextvar(arg.tpe)
                    insns.append(new xor(res, arg.asInstanceOf[LMValue[LMInt]], new CInt(arg.tpe, -1)))
                    stack.push((res,s))
                  }
                  case Arithmetic(op, k) => {
                    val (v2,ressym) = stack.pop
                    val v1 = stack.pop._1
                    val result = nextvar(typeKindType(k))
                    def choose(intop: =>LMInstruction, floatop: =>LMInstruction) {
                      v1.tpe match {
                        case _:LMInt => insns.append(intop)
                        case _:LMFloatingPointType => insns.append(floatop)
                      }
                    }
                    def cv[T <: ConcreteType](x: LocalVariable[_]) = x.asInstanceOf[LocalVariable[T]]
                    def c[T <: ConcreteType](x: LMValue[_]) = x.asInstanceOf[LMValue[T]]
                    def extend(v: LMValue[LMInt]) = {
                      val resultAsInt = result.asInstanceOf[LocalVariable[LMInt]]
                      if (v.tpe.bits < resultAsInt.tpe.bits) {
                        val temp = nextvar(resultAsInt.tpe)
                        insns.append(new sext(temp, v))
                        temp
                      } else {
                        v
                      }
                    }
                    op match {
                      case ADD => choose(new add(cv(result), extend(c(v1)), extend(c(v2))), new fadd(cv(result), c(v1), c(v2)))
                      case SUB => choose(new sub(cv(result), extend(c(v1)), extend(c(v2))), new fsub(cv(result), c(v1), c(v2)))
                      case MUL => choose(new mul(cv(result), extend(c(v1)), extend(c(v2))), new fmul(cv(result), c(v1), c(v2)))
                      case DIV => choose(new sdiv(cv(result), extend(c(v1)), extend(c(v2))), new fdiv(cv(result), c(v1), c(v2)))
                      case REM => choose(new srem(cv(result), extend(c(v1)), extend(c(v2))), new frem(cv(result), c(v1), c(v2)))
                    }
                    stack.push((result, ressym))
                  }
                  case Logical(op, _) => {
                    val (v2t,ressym) = stack.pop
                    val v2 = v2t.asInstanceOf[LMValue[LMInt]]
                    val v1 = stack.pop._1.asInstanceOf[LMValue[LMInt]]
                    val result = nextvar(v2.tpe)
                    val insn = op match {
                      case AND => new and(result, v1, v2)
                      case OR => new or(result, v1, v2)
                      case XOR => new xor(result, v1, v2)
                    }
                    insns.append(insn)
                    stack.push((result, ressym))
                  }
                  case Shift(op, _) => {
                    val (argt,ressym) = stack.pop
                    val arg = argt.asInstanceOf[LMValue[LMInt]]
                    val shiftamt = stack.pop._1.asInstanceOf[LMValue[LMInt]]
                    val temp = nextvar(arg.tpe)
                    val result = nextvar(arg.tpe)
                    insns.append(new zext(temp, shiftamt))
                    val insn = op match {
                      case LSL => new shl(result, arg, temp)
                      case ASR => new ashr(result, arg, temp)
                      case LSR => new lshr(result, arg, temp)
                    }
                    insns.append(insn)
                    stack.push((result, ressym))
                  }
                  case Conversion(_, dk) => {
                    val dt = typeKindType(dk)
                    val src = stack.pop._1
                    val result = nextvar(dt)
                    dt match {
                      case dit:LMInt => {
                        src.tpe match {
                          case sit:LMInt => if (dit.bits > sit.bits) {
                            insns.append(new sext(result.asInstanceOf[LocalVariable[LMInt]], src.asInstanceOf[LMValue[LMInt]]))
                          } else {
                            insns.append(new trunc(result.asInstanceOf[LocalVariable[LMInt]], src.asInstanceOf[LMValue[LMInt]]))
                          }
                          case sft:LMFloatingPointType => {
                            insns.append(new fptosi(result.asInstanceOf[LocalVariable[LMInt]], src.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]]))
                          }
                        }
                      }
                      case dft:LMFloatingPointType => {
                        src.tpe match {
                          case sit:LMInt => insns.append(new sitofp(result.asInstanceOf[LocalVariable[LMFloatingPointType with ConcreteType]], src.asInstanceOf[LMValue[LMInt]]))
                          case sft:LMFloatingPointType => if (dft.bits > sft.bits) {
                            insns.append(new fpext(result.asInstanceOf[LocalVariable[LMFloatingPointType with ConcreteType]], src.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]]))
                          } else {
                            insns.append(new fptrunc(result.asInstanceOf[LocalVariable[LMFloatingPointType with ConcreteType]], src.asInstanceOf[LMValue[LMFloatingPointType with ConcreteType]]))
                          }
                        }
                      }
                    }
                    stack.push((result,dk.toType.typeSymbol))
                  }
                  case _ => println("unsupported primitive op " + primitive)
                }
              }
              case CALL_METHOD(method, style) => {
                val fun = externFun(method)
                recordType(fun.resultType)
                fun.args.map(_.lmvar.tpe).foreach(recordType)
                val args = stack.take(fun.args.length).map(_._1).reverse
                val castedfun = new Cbitcast(new CFunctionAddress(fun), new LMFunctionType(fun.resultType, args.map(_.tpe), false).pointer)
                popn(fun.args.length)
                if (fun.tpe.returnType == LMVoid) {
                  insns.append(new call_void(fun.cconv, fun.ret_attrs, castedfun, args, fun.fn_attrs))
                } else {
                  val v = nextvar(fun.resultType)
                  insns.append(new call(v, fun.cconv, fun.ret_attrs, castedfun, args, fun.fn_attrs))
                  stack.push((v,method.tpe.resultType.typeSymbol))
                }
              }
              case NEW(kind) => {
                val asobject = nextvar(rtObject.pointer)
                insns.append(new call(asobject, rtNew, Seq(new CGlobalAddress(externClass(kind.toType.typeSymbol)))))
                val casted = nextvar(typeKindType(kind).pointer)
                recordType(casted.tpe)
                insns.append(new bitcast(casted, asobject))
              }
              case CREATE_ARRAY(elem, dims) => println("unhandled " + i)
              case IS_INSTANCE(tpe) => println("unhandled " + i)
              case CHECK_CAST(tpe) => println("unhandled " + i)
              case SWITCH(tags, labels) => {
                val v = stack.pop._1.asInstanceOf[LMValue[LMInt]]
                val deflabel = blockLabel(labels.last)
                val taggedlabels = tags.zip(labels.dropRight(1))
                val dests = taggedlabels.flatMap { case (tvs,b) =>
                  tvs.map(tv => (new CInt(v.tpe, tv), blockLabel(b)))
                }
                insns.append(new switch(v, deflabel, dests))
              }
              case JUMP(whereto) => insns.append(new br(blockLabel(whereto)))
              case CJUMP(success, failure, op, kind) => {
                val arg = stack.pop._1
                val cmpto = stack.pop._1
                val result = nextvar(LMInt.i1)
                def cmp(intop: ICond, floatop: FCond) = {
                  arg.tpe match {
                    case _:LMInt => insns.append(new icmp(result, intop, arg.asInstanceOf[LMValue[LMInt]], cmpto.asInstanceOf[LMValue[LMInt]]))
                    case _:LMFloat => insns.append(new fcmp(result, floatop, arg.asInstanceOf[LMValue[LMFloat]], cmpto.asInstanceOf[LMValue[LMFloat]]))
                    case _:LMDouble => insns.append(new fcmp(result, floatop, arg.asInstanceOf[LMValue[LMDouble]], cmpto.asInstanceOf[LMValue[LMFloat]]))
                    case _:LMPointer => insns.append(new icmp_p(result, intop, arg.asInstanceOf[LMValue[LMPointer]], cmpto.asInstanceOf[LMValue[LMPointer]]))
                  }
                }
                val cond = op match {
                  case EQ => cmp(ICond.ieq, FCond.oeq)
                  case NE => cmp(ICond.ine, FCond.one)
                  case LT => cmp(ICond.slt, FCond.olt)
                  case GE => cmp(ICond.sge, FCond.oge)
                  case LE => cmp(ICond.sle, FCond.ole)
                  case GT => cmp(ICond.sgt, FCond.ogt)
                }
                insns.append(new br_cond(result, blockLabel(success), blockLabel(failure)))
              }
              case CZJUMP(success, failure, op, kind) => {
                val arg = stack.pop._1
                val cmpto = new CZeroInit(arg.tpe)
                val result = nextvar(LMInt.i1)
                def cmp(intop: ICond, floatop: FCond) = {
                  arg.tpe match {
                    case _:LMInt => insns.append(new icmp(result, intop, arg.asInstanceOf[LMValue[LMInt]], cmpto.asInstanceOf[LMValue[LMInt]]))
                    case _:LMFloat => insns.append(new fcmp(result, floatop, arg.asInstanceOf[LMValue[LMFloat]], cmpto.asInstanceOf[LMValue[LMFloat]]))
                    case _:LMDouble => insns.append(new fcmp(result, floatop, arg.asInstanceOf[LMValue[LMDouble]], cmpto.asInstanceOf[LMValue[LMFloat]]))
                    case _:LMPointer => insns.append(new icmp_p(result, intop, arg.asInstanceOf[LMValue[LMPointer]], cmpto.asInstanceOf[LMValue[LMPointer]]))
                  }
                }
                val cond = op match {
                  case EQ => cmp(ICond.ieq, FCond.oeq)
                  case NE => cmp(ICond.ine, FCond.one)
                  case LT => cmp(ICond.slt, FCond.olt)
                  case GE => cmp(ICond.sge, FCond.oge)
                  case LE => cmp(ICond.sle, FCond.ole)
                  case GT => cmp(ICond.sgt, FCond.ogt)
                }
                insns.append(new br_cond(result, blockLabel(success), blockLabel(failure)))
              }
              case RETURN(kind) => {
                if (kind == UNIT) {
                  insns.append(retvoid)
                } else {
                  insns.append(new ret(stack.pop._1))
                }
              }
              case THROW() => println("unhandled " + i)
              case DROP(kind) => stack.pop
              case DUP(kind) => stack.push(stack.top)
              case MONITOR_ENTER() => println("unhandled " + i)
              case MONITOR_EXIT() => println("unhandled " + i)
              case SCOPE_ENTER(lv) => println("unhandled " + i)
              case SCOPE_EXIT(lv) => println("unhandled " + i)
              case LOAD_EXCEPTION() => println("unhandled " + i)
              case BOX(_) => {
                val (unboxed,unboxedsym) = stack.pop
                val fun = unboxed.tpe match {
                  case LMInt.i1 => rtBoxi1
                  case LMInt.i8 => rtBoxi8
                  case LMInt.i16 => rtBoxi16
                  case LMInt.i32 => rtBoxi32
                  case LMInt.i64 => rtBoxi64
                  case LMFloat => rtBoxFloat
                  case LMDouble => rtBoxDouble
                }
                val boxed = nextvar(fun.resultType)
                insns.append(new call(boxed, fun, Seq(unboxed)))
                stack.push((boxed, definitions.boxedClass(unboxedsym)))
              }
              case UNBOX(_) => {
                val (boxed,boxedsym) = stack.pop
                val (fun,sym) = boxedsym match {
                  case definitions.BoxedBooleanClass => (rtUnboxi1, definitions.BooleanClass)
                  case definitions.BoxedByteClass => (rtUnboxi8, definitions.ByteClass)
                  case definitions.BoxedShortClass => (rtUnboxi16, definitions.ShortClass)
                  case definitions.BoxedIntClass => (rtUnboxi32, definitions.IntClass)
                  case definitions.BoxedLongClass => (rtUnboxi64, definitions.LongClass)
                  case definitions.BoxedFloatClass => (rtUnboxFloat, definitions.FloatClass)
                  case definitions.BoxedDoubleClass => (rtUnboxDouble, definitions.DoubleClass)
                }
                val unboxed = nextvar(fun.resultType)
                insns.append(new call(unboxed, fun, Seq(unboxed)))
                stack.push((unboxed, sym))
              }
            }
          }
          producedTypes.zip(stack.takeRight(producedTypes.length)).zipWithIndex.foreach { case ((sym,(src,ssym)),n) =>
            val tpe = symType(sym)
            insns.insert(insns.length-1,new select(new LocalVariable(blockName(bb)+".out."+n.toString, tpe), CTrue, src, new CUndef(tpe)))
          }
          blocks.append(LMBlock(Some(blockLabel(bb)), insns))
        }
        fun.define(blocks)
      }

      val name = llvmName(c.symbol)
      val outfile = getFile(c.symbol, ".ll")
      val outstream = new OutputStreamWriter(outfile.bufferedOutput,"US-ASCII")
      val header_comment = new Comment("Module for " + c.symbol.fullName('.'))
      val methods = c.methods.map(genFun _)
      val externDecls = externFuns.filterKeys(!internalFuns.contains(_)).values.map(_.declare)++externClasses.values.map(_.declare)
      val module = new Module(Seq(header_comment)++externTypes.groupBy(_.name).values.map(ats => new TypeAlias(ats.first))++rtHeader++externDecls++externModules.values.map(_.declare)++classInfo++methods++extraDefs++moduleInfo)
      outstream.write(module.syntax)
      outstream.write("\n")
      outstream.close()
    }

    def constValue(c: Constant): LMConstant[_<:ConcreteType] = {
      c.tag match {
        case UnitTag => new CUndef(LMVoid)
        case BooleanTag => c.booleanValue
        case ByteTag => c.byteValue
        case ShortTag => c.shortValue
        case CharTag => c.shortValue
        case IntTag => c.intValue
        case LongTag => c.longValue
        case FloatTag => c.floatValue
        case DoubleTag => c.doubleValue
        case _ => {
          error("Can't handle " + c + " tagged " + c.tag)
          new CUndef(LMVoid)
        }
      }
    }

    def classType(s: Symbol): ConcreteType with AliasedType = {
      classes.get(s) match {
        case Some(c) => classType(c)
        case None => externalType(s)
      }
    }

    def classType(c: IClass): ConcreteType with AliasedType = {
      val s = c.symbol
      val fieldTypes = c.fields.map(fieldType _)
      val typelist = if (s.superClass != NoSymbol) classType(s.superClass)+:fieldTypes else fieldTypes
      new LMStructure(typelist).aliased(llvmName(s))
    }

    def fieldType(f: IField) = {
      symType(f.symbol)
    }

    def localType(l: Local) = {
      typeKindType(l.kind)
    }

    def typeKindType(tk: TypeKind) = {
      typeType(tk.toType)
    }

    def typeType(t: Type) = {
      t.typeSymbol match {
        case definitions.BooleanClass => LMInt.i1
        case definitions.ByteClass => LMInt.i8
        case definitions.ShortClass => LMInt.i16
        case definitions.IntClass => LMInt.i32
        case definitions.LongClass => LMInt.i64
        case definitions.FloatClass => LMFloat
        case definitions.DoubleClass => LMDouble
        case definitions.CharClass => LMInt.i16
        case definitions.UnitClass => LMVoid
        case x => externalType(x).pointer
      }
    }

    def externalType(s: Symbol) = {
      if (s == definitions.ObjectClass) {
        rtObject
      } else {
        LMOpaque.aliased(llvmName(s))
      }
    }

    def symType(s: Symbol): ConcreteType = {
      if (s.isMethod) {
        val baseArgTypes = s.tpe.paramTypes.map(typeType)
        val argTypes = if (s.isStaticMember) baseArgTypes else symType(s.owner) +: baseArgTypes
        new LMFunctionType(
          if (s.isClassConstructor) LMVoid else typeType(s.tpe.resultType),
          argTypes,
          false)
      } else {
        typeType(s.tpe)
      }
    }

    def getFile(sym: Symbol, suffix: String): AbstractFile = {
      val sourceFile = atPhase(currentRun.phaseNamed("llvm").prev)(sym.sourceFile)
      val dir: AbstractFile = settings.outputDirs.outputDirFor(sourceFile)
      dir.fileNamed(sym.fullName('_') + suffix)
    }

    def llvmName(sym: Symbol): String = {
      if (sym.isClass && !sym.isModule && !sym.isClassOfModule)
        sym.fullName('.')
      else if (sym.isClassOfModule || (sym.isModule && !sym.isMethod))
	sym.fullName('.')+"!module"
      else if (sym.isMethod)
        llvmName(sym.owner)+"/"+sym.simpleName.toString.trim()
      else
	sym.simpleName.toString.trim()
    }

    def blockLabel(bb: BasicBlock) = Label(blockName(bb))
    def blockName(bb: BasicBlock) = "bb."+bb.label

    def classInfoName(s: Symbol) = {
      ".classinfo."+llvmName(s)
    }

    def moduleInstanceName(s: Symbol) = {
      ".instance."+llvmName(s)
    }

    def stackUsage(bb: BasicBlock) = {
      /* first is deepest in produced stack */
      val produced = new mutable.ListBuffer[Symbol]
      val needed = new mutable.ListBuffer[Symbol]
      bb foreach { instruction =>
        val consumedTypes = instruction match {
          case RETURN(UNIT) => Nil
          case RETURN(k) => k.toType.typeSymbol::Nil
          case CJUMP(_,_,_,k) => Seq.fill(2)(k.toType.typeSymbol).toList
          case CALL_METHOD(m,SuperCall(_)) => m.owner.tpe.typeSymbol::Nil
          case i => i.consumedTypes.map(_.toType.typeSymbol)
        }
        val producedTypes = instruction match {
          case CALL_METHOD(m, _) => {
            if (m.tpe.resultType.typeSymbol == definitions.UnitClass)
              Nil
            else if (m.isConstructor)
              Nil
            else
              m.tpe.resultType.typeSymbol::Nil
          }
          case BOX(bt) => bt.toType.typeSymbol::Nil
          case UNBOX(bt) => bt.toType.typeSymbol::Nil
          case i => i.producedTypes.map(_.toType.typeSymbol)
        }
        if (instruction.consumed != consumedTypes.length) {
          println("Consumption mismatch for " + instruction)
          println(instruction.consumed.toString +" "+ consumedTypes)
        }
        if (instruction.produced != producedTypes.length) {
          println("Production mismatch for " + instruction)
          println(instruction.produced.toString +" "+ producedTypes)
        }
        val takenInternally = consumedTypes.length.min(produced.length)
        if (takenInternally > 0) produced.remove(produced.length-takenInternally, takenInternally) else ()
        needed.appendAll(consumedTypes.dropRight(takenInternally))
        produced.appendAll(producedTypes)
      }
      (needed.toList,produced.toList)
    }
  }
}
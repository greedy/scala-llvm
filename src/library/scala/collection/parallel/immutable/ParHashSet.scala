package scala.collection.parallel.immutable







import scala.collection.parallel.ParSet
import scala.collection.parallel.ParSetLike
import scala.collection.parallel.Combiner
import scala.collection.parallel.ParIterableIterator
import scala.collection.parallel.EnvironmentPassingCombiner
import scala.collection.parallel.UnrolledBuffer.Unrolled
import scala.collection.parallel.UnrolledBuffer
import scala.collection.generic.ParSetFactory
import scala.collection.generic.CanCombineFrom
import scala.collection.generic.GenericParTemplate
import scala.collection.generic.GenericParCompanion
import scala.collection.generic.GenericCompanion
import scala.collection.immutable.HashSet






/** Parallel hash trie set.
 *  
 *  @author prokopec
 */
class ParHashSet[T] private[immutable] (private[this] val trie: HashSet[T])
extends ParSet[T]
   with GenericParTemplate[T, ParHashSet]
   with ParSetLike[T, ParHashSet[T], HashSet[T]]
{
self =>
  
  def this() = this(HashSet.empty[T])
  
  override def companion: GenericCompanion[ParHashSet] with GenericParCompanion[ParHashSet] = ParHashSet
  
  override def empty: ParHashSet[T] = new ParHashSet[T]
  
  def parallelIterator: ParIterableIterator[T] = new ParHashSetIterator(trie.iterator, trie.size) with SCPI
  
  def seq = trie
  
  def -(e: T) = new ParHashSet(trie - e)
  
  def +(e: T) = new ParHashSet(trie + e)

  def contains(e: T): Boolean = trie.contains(e)
  
  override def size = trie.size
  
  protected override def reuse[S, That](oldc: Option[Combiner[S, That]], newc: Combiner[S, That]) = oldc match {
    case Some(old) => old
    case None => newc
  }
  
  type SCPI = SignalContextPassingIterator[ParHashSetIterator]
  
  class ParHashSetIterator(val triter: Iterator[T], val sz: Int)
  extends super.ParIterator {
  self: SignalContextPassingIterator[ParHashSetIterator] =>
    var i = 0
    def split: Seq[ParIterator] = if (remaining < 2) Seq(this) else triter match {
      case t: HashSet.TrieIterator[_] =>
        val previousRemaining = remaining
        val ((fst, fstlength), snd) = t.asInstanceOf[HashSet.TrieIterator[T]].split
        val sndlength = previousRemaining - fstlength
        Seq(
          new ParHashSetIterator(fst, fstlength) with SCPI,
          new ParHashSetIterator(snd, sndlength) with SCPI
        )
      case _ =>
        // iterator of the collision map case
        val buff = triter.toBuffer
        val (fp, sp) = buff.splitAt(buff.length / 2)
        Seq(fp, sp) map { b => new ParHashSetIterator(b.iterator, b.length) with SCPI }
    }
    def next: T = {
      i += 1
      triter.next
    }
    def hasNext: Boolean = {
      i < sz
    }
    def remaining = sz - i
  }
  
}


object ParHashSet extends ParSetFactory[ParHashSet] {
  def newCombiner[T]: Combiner[T, ParHashSet[T]] = HashSetCombiner[T]
  
  implicit def canBuildFrom[T]: CanCombineFrom[Coll, T, ParHashSet[T]] =
    new GenericCanCombineFrom[T]
  
  def fromTrie[T](t: HashSet[T]) = new ParHashSet(t)
}


private[immutable] abstract class HashSetCombiner[T]
extends collection.parallel.BucketCombiner[T, ParHashSet[T], Any, HashSetCombiner[T]](HashSetCombiner.rootsize) {
self: EnvironmentPassingCombiner[T, ParHashSet[T]] =>
  import HashSetCombiner._
  val emptyTrie = HashSet.empty[T]
  
  def +=(elem: T) = {
    sz += 1
    val hc = emptyTrie.computeHash(elem)
    val pos = hc & 0x1f
    if (buckets(pos) eq null) {
      // initialize bucket
      buckets(pos) = new UnrolledBuffer[Any]
    }
    // add to bucket
    buckets(pos) += elem
    this
  }
  
  def result = {
    val bucks = buckets.filter(_ != null).map(_.headPtr)
    val root = new Array[HashSet[T]](bucks.length)
    
    executeAndWaitResult(new CreateTrie(bucks, root, 0, bucks.length))
    
    var bitmap = 0
    var i = 0
    while (i < rootsize) {
      if (buckets(i) ne null) bitmap |= 1 << i
      i += 1
    }
    val sz = root.foldLeft(0)(_ + _.size)
    
    if (sz == 0) new ParHashSet[T]
    else if (sz == 1) new ParHashSet[T](root(0))
    else {
      val trie = new HashSet.HashTrieSet(bitmap, root, sz)
      new ParHashSet[T](trie)
    }
  }
  
  /* tasks */
  
  class CreateTrie(bucks: Array[Unrolled[Any]], root: Array[HashSet[T]], offset: Int, howmany: Int)
  extends super.Task[Unit, CreateTrie] {
    var result = ()
    def leaf(prev: Option[Unit]) = {
      var i = offset
      val until = offset + howmany
      while (i < until) {
        root(i) = createTrie(bucks(i))
        i += 1
      }
    }
    private def createTrie(elems: Unrolled[Any]): HashSet[T] = {
      var trie = new HashSet[T]
      
      var unrolled = elems
      var i = 0
      while (unrolled ne null) {
        val chunkarr = unrolled.array
        val chunksz = unrolled.size
        while (i < chunksz) {
          val v = chunkarr(i).asInstanceOf[T]
          val hc = trie.computeHash(v)
          trie = trie.updated0(v, hc, rootbits)
          i += 1
        }
        i = 0
        unrolled = unrolled.next
      }
      
      trie
    }
    def split = {
      val fp = howmany / 2
      List(new CreateTrie(bucks, root, offset, fp), new CreateTrie(bucks, root, offset + fp, howmany - fp))
    }
    def shouldSplitFurther = howmany > collection.parallel.thresholdFromSize(root.length, parallelismLevel)
  }
  
}


object HashSetCombiner {
  def apply[T] = new HashSetCombiner[T] with EnvironmentPassingCombiner[T, ParHashSet[T]] {}
  
  private[immutable] val rootbits = 5
  private[immutable] val rootsize = 1 << 5
}


























































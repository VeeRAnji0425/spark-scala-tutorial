import com.typesafe.sparkworkshop.util.CommandLineOptions
import com.typesafe.sparkworkshop.util.CommandLineOptions.Opt
import com.typesafe.sparkworkshop.util.Matrix
import org.apache.spark.SparkContext

/**
 * Use an explicitly-parallel algorithm to sum perform statistics on
 * rows in matrices. This version adds computation of the standard deviation.
 */
object Matrix4StdDev {

  case class Dimensions(m: Int, n: Int)

  def main(args: Array[String]) = {

    /** A function to generate an Opt for handling the matrix dimensions. */
    def dims(value: String): Opt = Opt(
      name   = "dims",
      value  = value,
      help   = s"-d | --dims  nxm     The number of rows (n) and columns (m) (default: $value)",
      parser = {
        case ("-d" | "--dims") +: nxm +: tail => (("dims", nxm), tail)
      })

    val options = CommandLineOptions(
      this.getClass.getSimpleName,
      CommandLineOptions.outputPath("output/matrix-math-stddev"),
      CommandLineOptions.master("local"),
      CommandLineOptions.quiet,
      dims("5x10"))

    val argz = options(args.toList)

    val dimsRE = """(\d+)\s*x\s*(\d+)""".r
    val dimensions = argz("dims").toString match {
      case dimsRE(m, n) => Dimensions(m.toInt, n.toInt)
      case s =>
        println("""Expected matrix dimensions 'NxM', but got this: $s""")
        sys.exit(1)
    }

    val sc = new SparkContext(argz("master").toString, "Matrix (4)")

    try {
      // Set up a mxn matrix of numbers.
      val matrix = Matrix(dimensions.m, dimensions.n)

      // Average rows of the matrix in parallel:
      val sums_avgs = sc.parallelize(1 to dimensions.m).map { i =>
        // Matrix indices count from 0.
        // "_ + _" is the same as "(count1, count2) => count1 + count2".
        val row = matrix(i-1)
        val sum = row reduce (_ + _)
        val avg = sum/dimensions.n
        val sumsquares = row.map(x => x*x).reduce(_+_)
        val stddev = math.sqrt(1.0*sumsquares) // 1.0* => so we get a double sqrt!
        (sum, avg, stddev)
      }.collect    // convert to an array

      // Make a new sequence of strings with the formatted output, then we'll
      // dump to the output location.
      val outputLines = Vector(          // Scala's Vector, not MLlib's version!
        s"${dimensions.m}x${dimensions.n} Matrix:") ++ sums_avgs.zipWithIndex.map {
        case ((sum, avg, stddev), index) =>
          f"Row #${index}%2d: Sum = ${sum}%4d, Avg = ${avg}%3d, Std. Dev = ${stddev}%.1f"
      }
      val output = sc.makeRDD(outputLines)  // convert back to an RDD
      val out = argz("output-path").toString
      if (argz("quiet").toBoolean == false)
        println(s"Writing output to: $out")
      output.saveAsTextFile(out)

    } finally {
      sc.stop()
    }

    // Exercise: Try different values of m, n.
    // Exercise: Try other statistics, like standard deviation.
    // Exercise: Try other statistics, like standard deviation. Are the average
    //   and standard deviation very meaningful here?
  }
}

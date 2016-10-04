import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclaratorId;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;

// Run with command
// mvn exec:java -Dexec.mainClass="LogMissingStackTrace" -Dexec.args="/Users/weichiu/sandbox/hadoop"

/**
 * Created by weichiu on 9/6/16.
 */
public class LogMissingStackTrace {
  public final static NameExpr LOG_EXPR = new NameExpr("LOG");
  public static void main(String[] args) throws Exception {
    assert args.length >= 1;
    File rootDir = new File(args[0]);

    IOFileFilter javaFileFilter = new WildcardFileFilter("*.java");
    Iterator<File> files = FileUtils.iterateFiles(
        rootDir, javaFileFilter, DirectoryFileFilter.DIRECTORY);

    while (files.hasNext()) {
      File file = files.next();
      System.out.println(file);
      parseCode(file);
    }
  }

  static void parseCode(File file) throws IOException, ParseException {
    // creates an input stream for the file to be parsed
    FileInputStream in = new FileInputStream(file.getPath());

    CompilationUnit cu;
    try {
      // parse the file
      cu = JavaParser.parse(in);
    } finally {
      in.close();
    }

    MyVisitor visitor = new MyVisitor();
    visitor.visit(cu, null);

    // prints the resulting compilation unit to default system output
    //System.out.println(cu.toString());
  }

  static class MyVisitor extends VoidVisitorAdapter<String> {
    @Override
    public void visit(final CatchClause c, final String args) {
      Parameter param = c.getParam();
      VariableDeclaratorId var = param.getId();
      super.visit(c, var.getName());
    }

    @Override
    public void visit(final MethodCallExpr mce, final String args) {
      super.visit(mce, args);
      // find LOG object method invocation.
      if (mce.getScope() != null && mce.getScope().equals(LOG_EXPR) &&
          hasMessage(mce)) {
        List<Expression> expressions =  mce.getArgs();

        if (args != null && args.length() > 0 ){
          if(!hasArg(args, expressions)) {
            System.out.println(mce.getBegin().line + ":" + mce.toString() +
                ": The logger did not use exception");
          }
        }
      }
    }

    private boolean hasMessage(MethodCallExpr mce) {
      String methodName = mce.getName();
      return (methodName.equals("trace") ||
          methodName.equals("debug") ||
          methodName.equals("info") ||
          methodName.equals("warn") ||
          methodName.equals("error") ||
          methodName.equals("fatal"));
    }

    private boolean hasArg(final String arg,
        final List<Expression> expressions) {
      for (Expression exp: expressions) {
        if (exp instanceof NameExpr) {
          if (((NameExpr)exp).getName().equals(arg)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}

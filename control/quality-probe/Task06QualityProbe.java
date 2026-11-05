import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.parser.PmdJspAnalyzer;
import com.comus93.specgen.parser.SourceAnalysisException;
public class Task06QualityProbe {
  static void show(PmdJspAnalyzer a, Path p) {
    System.out.println("CASE="+p.getFileName());
    try {
      JspFacts f=a.analyze(p);
      System.out.println("COUNTS fields="+f.screenFields().size()+" flows="+f.flows().size()+" events="+f.events().size()+" constants="+f.constants().size());
      for(var x:f.screenFields()) System.out.println("FIELD "+x.fieldName()+" | object="+x.object()+" | ref="+x.referenceField()+" | editable="+x.editable()+" | type="+x.fieldType()+" | attrs="+x.rawAttributes());
      for(var x:f.events()) System.out.println("EVENT "+x.name()+" | description="+x.description()+" | return="+x.returnValue());
      int counter=0;
      for(var x:f.flows()) {
        if(counter<3 || x.tagOrFile().contains("include") || x.tagOrFile().contains("header.jspf") || x.tagOrFile().contains("c:forEach") || x.tagOrFile().equals("EL")) {
          System.out.println("FLOW "+x.nodeKind()+" | "+x.tagOrFile()+" | "+x.attributes());
        }
        counter++;
      }
    } catch(SourceAnalysisException e) { System.out.println("ERROR "+e.errorCode()+" | location="+e.location()); }
  }
  public static void main(String[] args) throws Exception {
    Path dir=Path.of(System.getProperty("java.io.tmpdir"),"task06-independent-review-20261106");
    Files.createDirectories(dir);
    Path custom=dir.resolve("review-custom.jsp");
    Files.writeString(custom,
        "<%@ include file=\"partials/header.jspf\" %>\n"+
        "<form:form commandName=\"invoice\"><form:input path=\"amount\" readonly=\"true\" /></form:form>\n"+
        "<a href=\"/view\"><strong>View details</strong></a><a onclick=\"js()\">Ignored</a>\n",
        StandardCharsets.UTF_8);
    Path malformed=dir.resolve("review-malformed.jsp");
    Files.writeString(malformed,"<c:forEach items=\"value\"",StandardCharsets.UTF_8);
    PmdJspAnalyzer analyzer=new PmdJspAnalyzer();
    Path fixtures=Path.of(args[0],"src/test/resources/fixtures/jsp");
    show(analyzer, fixtures.resolve("todo.jsp"));
    show(analyzer, fixtures.resolve("list-todos.jsp"));
    show(analyzer,custom);
    show(analyzer,malformed);
  }
}
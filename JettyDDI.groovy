@Grapes([
          @Grab('org.eclipse.jetty:jetty-server:9.0.0.M5'),
          @Grab('org.eclipse.jetty:jetty-servlet:9.0.0.M5'),
          @Grab('javax.servlet:javax.servlet-api:3.0.1'),
          @GrabExclude('org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016'),
          @GrabConfig(systemClassLoader=true)
        ])
 
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*

interactionmap = [:].withDefault { new LinkedHashSet() }

id2labels = [:].withDefault { new LinkedHashSet() }
def parseLabelFiles() {
  println "Parsing labels..."
  new File("sider/label_mapping.tsv").splitEachLine("\t") { line ->
    def lab1 = line[0].split(";")
    def lab2 = line[1].split(";")
    def id = line[-1]
    id2labels[id].addAll(lab1)
    id2labels[id].addAll(lab2)
  }
}

def parseInteractionFiles() {
  println "Parsing drug interaction file(s)..."
  new File("CombinedDatasetConservativeTWOSIDES.csv").splitEachLine("\t") { line ->
    if (line[0]!="drug1") {
      Expando exp = new Expando()
      exp.d1 = line[0]
      exp.dn1 = line[1]
      exp.d2 = line[2]
      exp.dn2 = line[3]
      exp.description = line[11]
      exp.evidence = line[18]
      interactionmap[exp.d1].add(exp)
    }
  }
}

def startJetty() {
  println "Starting Jetty..."
  def server = new Server(31339)
  def context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
  context.setAttribute('interactionmap',interactionmap)
  context.resourceBase = '.'  
  context.addServlet(GroovyServlet, '/DDIServlet.groovy')  
  //  context.addServlet(GroovyServlet, '/DermoServlet.groovy')  
  context.setAttribute('version', '1.0')  
  server.start()
}

parseLabelFiles()
parseInteractionFiles()
startJetty()

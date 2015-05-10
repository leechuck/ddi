import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*
import org.apache.lucene.search.highlight.*
import groovy.json.*

String indexPath = "drug-name-index"

def jsonslurper = new JsonSlurper()

if (!application) {
  application = request.getApplication(true);
}

if (!application.searcher) {
  Directory dir = FSDirectory.open(new File(indexPath)) 
  Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)

  DirectoryReader reader = DirectoryReader.open(dir)
  IndexSearcher searcher = new IndexSearcher(reader)

  QueryParser parser = new QueryParser(Version.LUCENE_47, "label", analyzer)

  application.parser = parser
  application.searcher = searcher
  application.analyzer = analyzer

}

def parser = application.parser
def searcher = application.searcher
def analyzer = application.analyzer
QueryBuilder builder = new QueryBuilder(analyzer)

def interactionmap = application.interactionmap

def query = request.getParameter("query")?.toLowerCase()
def namequery = request.getParameter("label")?.toLowerCase()


if (namequery) {
  Term term = new Term("label", namequery)
  
  //  println namequery
  //Query q = builder.createPhraseQuery("label", "$namequery")
  Query q = new PrefixQuery(term)
  ScoreDoc[] hits = searcher.search(q, null, 10000, Sort.RELEVANCE, true, true).scoreDocs
  def numhits = hits.size()
  List<Map<String, Set<String>>> response = []
  hits.each { doc ->
    Document hitDoc = searcher.doc(doc.doc)
    hitDoc.getValues("label").each { lab ->
      if (lab.startsWith(namequery)) {
	Map m = [:].withDefault { new TreeSet() }
	hitDoc.getValues("id").each {
	  if (it && it.size()>1) {
	    m[lab].add(it)
	  }
	}
	response << m
	//	response[lab] = s
	//	println hitDoc.getValues("id")+"\t"+lab
      }
    }
  }
  JsonBuilder jsonbuilder = new JsonBuilder(response)
  println jsonbuilder.toPrettyString()
} else {
  println interactionmap[query]
}

import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*
import com.aliasi.medline.*
import org.apache.lucene.analysis.fr.*
import opennlp.tools.sentdetect.*
import opennlp.tools.dictionary.*
import opennlp.tools.tokenize.*
import opennlp.tools.util.*
import opennlp.tools.chunker.*
import opennlp.tools.postag.*
import opennlp.tools.namefind.*
import java.util.concurrent.*


String indexPath = "drug-name-index"

Directory dir = FSDirectory.open(new File(indexPath))
Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)
IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer)
iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
iwc.setRAMBufferSizeMB(32768.0)
IndexWriter writer = new IndexWriter(dir, iwc)

FieldType fieldType = new FieldType()
fieldType.setStoreTermVectors(true)
fieldType.setStoreTermVectorPositions(true)
fieldType.setStoreTermVectorOffsets(true)
fieldType.setStoreTermVectorPayloads(true)
fieldType.setIndexed(true)
fieldType.setTokenized(true)
fieldType.setStored(true)
fieldType.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)

id2labels = [:].withDefault { new LinkedHashSet() }
labels2id = [:].withDefault { new LinkedHashSet() }
println "Parsing labels..."
new File("sider/label_mapping.tsv").splitEachLine("\t") { line ->
  def lab1 = line[0].toLowerCase().trim()
  def lab2 = line[1].split(";").collect { it.toLowerCase().trim() }
  def id = line[-1]
  id2labels[id].add(lab1)
  // generate set of labels from all the components of a compound drug
  for (int i = 0 ; i < lab2.size() ; i++) {
    def templab = lab2[i]
    for (j = 0 ; j < lab2.size() ; j++) {
      if (i!=j) {
	templab += " + "+lab2[j]
      }
    }
    id2labels[id].addAll(templab)
  }
  labels2id[lab1].add(id)
  lab2.each { lab ->
    labels2id[lab].add(id)
  }
}

// at this point, compound drugs (defined by label) will be mapped to the sider id, and the id to all the labels

id2labels2 = [:].withDefault { new LinkedHashSet() }
labels2id2 = [:].withDefault { new LinkedHashSet() }
println "Parsing drug interaction file(s)..."
new File("CombinedDatasetConservativeTWOSIDES.csv").splitEachLine("\t") { line ->
  if (line[0]!="drug1") {
    def d1 = line[0]
    def dn1 = line[1].toLowerCase().trim()
    def d2 = line[2]
    def dn2 = line[3]?.toLowerCase()?.trim()
    id2labels2[d1].add(dn1)
    id2labels2[d2].add(dn2)
    labels2id2[dn1].add(d1)
    labels2id2[dn2].add(d2)
  }
}

def doneSet = new LinkedHashSet()
id2labels2.each { id, labels ->
  labels.each { label ->
    if (label && label!="null" && !(label in doneSet)) {
      Document doc = new Document()
      doc.add(new Field("id", id, Field.Store.YES, Field.Index.NO))
      doc.add(new Field("label", label, fieldType))
      doneSet.add(label)
      writer.addDocument(doc)
    }
  }
}

// now SIDER
id2labels.each { id, labels ->
  def idset = new LinkedHashSet()
  labels.each { lab ->
    def l = []
    if (lab.indexOf(" + ") > -1) {
      l = lab.split(" \\+ ").collect { it.trim() }
    } else {
      l << lab
    }
    l.each { label ->
      labels2id2[label]?.each { idset.add(it) }
    }
  }
  if (idset.size()>0) {
    labels.each { label ->
      if (label && label.length()>1 && !(label in doneSet)) {
	Document doc = new Document()
	doc.add(new Field("label", label, fieldType))
	idset.each { 
	  doc.add(new Field("id", it, Field.Store.YES, Field.Index.NO))
	}
	doneSet.add(label)
	writer.addDocument(doc)
      }
    }
  }
}

writer.close()


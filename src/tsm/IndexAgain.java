/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import static tsm.WordVecIndexer.FIELD_BAG_OF_WORDS;
import static tsm.WordVecIndexer.FIELD_ID;

/**
 *
 * @author riya
 */
public class IndexAgain {
    
    
    Properties prop;
    File indexDir;
    File wvIndexDir;
    WordVecs wordvecs;
    IndexWriter writer;
    PerFieldAnalyzerWrapper wrapper;
    int indexingPass;
    
    //List<String> stopwords;
    
    static final public String FIELD_BAG_OF_WORDS = "words";
    static final public String FIELD_ID ="id";
    
    HashMap<String,Integer> uterm = new HashMap<String,Integer>(); 
    
    public void expandIndex(String st) throws Exception {
        
        
        
        prop=new Properties();
        prop.load(new FileReader(st));
        String indexPath = prop.getProperty("word.index");
        String wvIndexPath = prop.getProperty("wv.index");
        wvIndexDir = new File(wvIndexPath);
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9, new WhitespaceAnalyzer(Version.LUCENE_4_9));
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
        
        indexDir = new File(indexPath);
        writer = new IndexWriter(FSDirectory.open(wvIndexDir), iwcfg);
        
        String str = prop.getProperty("unique_list");
        String line;
        try {
            int count=1;
            FileReader fr = new FileReader(str);
            BufferedReader br = new BufferedReader(fr);
            while ( (line = br.readLine()) != null ) {
                
                String[] tokens = line.split("\\s+");
                uterm.put(tokens[0],count);
                count=count+1;
            }
            br.close(); fr.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }       
        
        
        
//        int start = Integer.parseInt(prop.getProperty("wv_expand.start.docid"));
        int end = Integer.parseInt(prop.getProperty("wv_expand.end.docid", "-1"));
       
        Document expDoc = new Document();
        
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
            int maxDoc = reader.maxDoc();
            end = Math.min(end, maxDoc);
            if (end == -1)
                end = maxDoc;

            for (int i = 0; i < end; i++) {
                System.out.println("DocId: " + i);                
                expDoc = expandDoc(reader, i);
                writer.addDocument(expDoc);
            }
        }
        
        writer.close();
    }
    
    public Document expandDoc(IndexReader reader,int docId) throws IOException
    {
        Document newdoc = new Document();
        Document doc = reader.document(docId);
        
        Terms terms = reader.getTermVector(docId, FIELD_BAG_OF_WORDS);
        if (terms == null || terms.size() == 0)
            return doc;
        
        TermsEnum termsEnum = terms.iterator(null);
        BytesRef term;
        StringBuffer buffer = new StringBuffer();
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            String termStr = term.utf8ToString();
            if(uterm.get(termStr)!=null)
            {
                buffer.append(termStr).append(" ");
            }              
        }
        newdoc.add(new Field(FIELD_ID, doc.get(FIELD_ID), Field.Store.YES, Field.Index.NOT_ANALYZED));
        newdoc.add(new Field(FIELD_BAG_OF_WORDS,buffer.toString(),Field.Store.YES, Field.Index.ANALYZED,Field.TermVector.YES));
        
        return newdoc;
   }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            args = new String[2];
            args[0] ="/home/riya/IR/glm/tweet.index.properties";
        }
        try {
            
            IndexAgain obj = new IndexAgain();
            obj.expandIndex(args[0]);
        }
        
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    
   
 }  

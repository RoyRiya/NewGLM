/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

/**
 *
 * @author riya
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;
import org.apache.lucene.analysis.payloads.NumericPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.tartarus.snowball.ext.PorterStemmer;
import static tsm.WordVecIndexer.FIELD_BAG_OF_WORDS;
import static tsm.WordVecIndexer.FIELD_ID;
//import static tsm.GloveVecIndexer.FIELD_ID;
//import static tsm.GloveVecIndexer.PAYLOAD_DELIM;
class GLMTermWeight1{
    String term;
    float prob_in_doc;  // for storing probability in document (sum of t' P(t,t'|d) )
    float prob_in_ng;  // for storing total neighbouring frequency (sum of t' P(t,t'|C) )
    float ntf_d;      // term frequency / doc_length
    float normalizer;     // total probability for normalizing 
    
    public GLMTermWeight1(String term1){
    	this.term=term1;
    }

}

public class SecondPassIndexNewGlove {
    
    Properties prop;
    File indexDir;
    File wvIndexDir;
    GloveVectors wordvecs;
    IndexWriter writer;
    int indexingPass;
    Compute_Yass_Distance yass_obj;
    float lambda;
    IndexReader reader;
    
    
    static final public  String FIELD_ID = "id"; //This field will have doc id
    static final public  String FIELD_TERM = "term"; // This field will have term
    static final public  String FIELD_WEIGHT ="weight"; //This field will store the weight as
    // lm_wt|glm_doc_trans_wt|glm_coll_trans_wt as a piped string
    static final public  String FIELD_LM = "lm_wt"; 
    static final public  String FIELD_GLM_DOC_TRANSFORM = "glm_doc_trans_wt";
    static final public  String FIELD_GLM_COLL_TRANSFORM = "glm_coll_trans_wt";
    
    
    public  SecondPassIndexNewGlove(String propFile) throws FileNotFoundException, IOException
    {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        String indexPath = prop.getProperty("word.index");
        String wvIndexPath = prop.getProperty("wv.index");
        wvIndexDir = new File(wvIndexPath);
        indexDir = new File(indexPath);
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9,new WhitespaceAnalyzer(Version.LUCENE_4_9));
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
         writer = new IndexWriter(FSDirectory.open(wvIndexDir), iwcfg);
         lambda = Float.parseFloat(prop.getProperty("comb_lambda"));
     
    }
    
    public void expandIndex() throws Exception
    {
        
        wordvecs = new GloveVectors(prop);
        yass_obj = new Compute_Yass_Distance();  //creating object to access the function call of yass
        
        if (wordvecs.wordvecmap != null)
            wordvecs.loadPrecomputedNNs();    // This will load the union of both wordtovec and yass neighbours 
        
        System.out.println("word-to-vec read and NNfile loaded");
        
        int end = Integer.parseInt(prop.getProperty("wv_expand.end.docid", "-1"));
       
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            int maxDoc = reader.maxDoc();
            end = Math.min(end, maxDoc);
            if (end == -1)
                end = maxDoc;

            for (int i = 0; i < end; i++) {
                System.out.println("DocId: " + i);                
                expandDoc(reader, i);
            }
        
        writer.close();
    }
    
    public int getDocLen(Terms terms) throws IOException
    {
        int docLen=0;
        
    	TermsEnum termsEnum = terms.iterator(null);
    	while (termsEnum.next() != null)
        {
        	DocsEnum docsEnum = termsEnum.docs(null, null);

            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            {
                //get the term frequency in the document
                docLen += docsEnum.freq();
            }
        }
		return docLen;
    }
    
    List<GLMTermWeight1> computeNormalizedTf(Terms terms, int docLen) throws IOException
    {
        BytesRef term;
        ArrayList<GLMTermWeight1> tsim = new ArrayList<>();
    	TermsEnum termsEnum = terms.iterator(null);
        
        while ((term = termsEnum.next()) != null) { 
            String termStr = term.utf8ToString();
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one
            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                int tf = docsEnum.freq();
                float ntf = tf/(float)docLen;
                GLMTermWeight1 tobj = new GLMTermWeight1(termStr);
                tobj.ntf_d = ntf;
                tsim.add(tobj);
            }
        }
		return tsim;
    }
    
    List<GLMTermWeight1> computeDocTransformProbs(List<GLMTermWeight1> tstats)
    {
    	int i, j, len = tstats.size();
        float  sim, totalPairwiseSim; // This variable will store the summation{P(t,t')} per term where t' belongs to the document 

        for (i = 0; i < len; i++) {
            GLMTermWeight1 tf_i = tstats.get(i);
            totalPairwiseSim = 0;
            for (j = 0; j < len; j++)
            {
                if (i==j)
                    continue;
                GLMTermWeight1 tf_j = tstats.get(j);
                sim = lambda*this.wordvecs.getSim(tf_i.term, tf_j.term) + (1-lambda)*(yass_obj.yass_similarity_mod(tf_i.term, tf_j.term));
		totalPairwiseSim += sim;
            }
            tf_i.normalizer=totalPairwiseSim;
            //storing the normalizer factor per term , which is the denominator.
            
            
        }
        
        // This module will store the calculate P(t,t'|d) = [sim(t,t')/ (normalizer of t)][tf(t',d)/|d|)]
        
        
        
        for (i = 0; i < len; i++)
        {
            GLMTermWeight1 tf_i = tstats.get(i);
            tf_i.prob_in_doc=0.0f;
            for(j=0;j<len;j++)
            {
                if(i==j)
                  continue;
                GLMTermWeight1 tf_j = tstats.get(j);
                sim = lambda*this.wordvecs.getSim(tf_i.term, tf_j.term) + (1-lambda)*(yass_obj.yass_similarity_mod(tf_i.term, tf_j.term));
                if(Float.compare(tf_i.normalizer,0.0f)!=0)
                {
                    tf_i.prob_in_doc+= (sim/tf_i.normalizer)*(tf_j.ntf_d);
                }
            }
        }
        
        return tstats;
    }
    
  public List<GLMTermWeight1> AveragingWeights(HashMap<String,Integer> hash , List<GLMTermWeight1> tstats)
  {
      int len=tstats.size();
      List<GLMTermWeight1> expandedTransformedList = new ArrayList<GLMTermWeight1>();
      for(String st : hash.keySet())
      {
          if(hash.get(st)==1) // If the term has occured only once in the doc and expanded doc
          {
             for(int i=0;i<len;i++)
             {
                 if(tstats.get(i).term.equals(st))
                 {
                    expandedTransformedList.add(tstats.get(i));
                    break;
                 }    
             }
          }
          else // we have to take the average of all the prpbability
          {
              float term_prob=0,doc_prob=0,coll_prob=0;
              for(int i=0;i<len;i++)
              {
                 if(tstats.get(i).term.equals(st))
                 {
                    term_prob+=tstats.get(i).ntf_d;
                    doc_prob+=tstats.get(i).prob_in_doc;
                    coll_prob+=tstats.get(i).prob_in_ng;
                 }
               }
                term_prob/=hash.get(st); // taking the average probability
                doc_prob/=hash.get(st);
                coll_prob/=hash.get(st);
                GLMTermWeight1 gm = new GLMTermWeight1(st); 
                gm.ntf_d=term_prob;
                gm.prob_in_doc=doc_prob;
                gm.prob_in_ng=coll_prob;
                expandedTransformedList.add(gm);
           }    
              
       }
            return expandedTransformedList;
      }
      
    
  public HashMap getUniqueTerms(List<GLMTermWeight1> tstats) // This method returns the unique_terms of the document along with the frequency 
  {
      HashMap<String,Integer> unique_terms = new HashMap<String,Integer>();
      int i,len=tstats.size();
      for(i=0;i<len;i++)
      {
          unique_terms.put(tstats.get(i).term,1);
      }
      
      return unique_terms;
  }
  
  public int check_presence_in_collection(String term) throws IOException
  {
    Term termInstance = new Term(FIELD_BAG_OF_WORDS,term);                              
    int termFreq = (int)reader.totalTermFreq(termInstance);  //collection frequency of the given term is obatined    
    return termFreq;
  }
    
  public  List<GLMTermWeight1> computeCollTransformProb(List<GLMTermWeight1> tstats, int K, float thresh) throws IOException 
  {
        List<GLMTermWeight1> expandedAndTransformedTermStats = new ArrayList<GLMTermWeight1>(tstats); // copy the objects,this list is to grow with new terms
	//List<GLMTermWeight1> mapOfExpandedTerms = new ArrayList<GLMTermWeight1>(); // need to accumulate weights
	int len = tstats.size(),i;
        
        HashMap<String,Integer> unique_terms=getUniqueTerms(tstats); // storing the unique terms of doc
        // This hash map now contains terms of document but later on the extended terms of the collection
        // will also be added.
        
        
        for (i = 0; i < len; i++)
        {   
            GLMTermWeight1 ts_i = tstats.get(i);
            ts_i.prob_in_ng=0; // initially assignment
            List<GloveVec> nn_tf_i = wordvecs.getPrecomputedNNs(ts_i.term, K, thresh);
            if (nn_tf_i == null || nn_tf_i.size() == 0) {
                continue;
            }
            
            float normalizer = 0;
            for (GloveVec nn : nn_tf_i)
            {
                if(check_presence_in_collection(nn.word)!=0)
                    normalizer += nn.querySim;
            }
            for (GloveVec nn : nn_tf_i)
            {
               if(check_presence_in_collection(nn.word)!=0)
               {
                   if(unique_terms.get(nn.word)==null)
                    unique_terms.put(nn.word,1);
                   else
                    unique_terms.put(nn.word,unique_terms.get(nn.word)+1); // This is counting number of times that word has occured 
                                                                         // useful for calculating average probability of same terms occuring 
                    GLMTermWeight1 tm= new GLMTermWeight1(nn.word); // expanded terms will not have prob_in_doc or ntf_d
                    tm.prob_in_doc=0;
                    tm.ntf_d=0;
                
                 
		// contribution of t_j to t_i is identical to that of t_i to t_j
                    tm.prob_in_ng = nn.querySim/normalizer;  // expanded term will only have this probability
		
                    ts_i.prob_in_ng += nn.querySim/normalizer; // updating the prob_in_ng field for a term t in doc          
               
                    expandedAndTransformedTermStats.add(tm); // each of the neighbouring terms are initially added and the list is expande
               }
            }
        }
        
        // Two possible case can arise 1. The neigbours terms added may be same as some of the document terms
                                    // 2. Some of the neighbours added could be alike
                // In both of these cases we will have more than one probabilities for a single term
                // In such case we will make average of the probabilities and extract distinct terms.
                                    

		// The contribution for each new (unique) term is accumulated in this map. Simply
		// add each element to the transformed array.
		
                List<GLMTermWeight1> new_transformed_list=AveragingWeights(unique_terms,expandedAndTransformedTermStats); 
		return new_transformed_list;
	}
  
  public void expandDoc(IndexReader reader, int docId) throws IOException, Exception
  {
        Document doc = reader.document(docId);
        BytesRef term;
        
        
         // set k=2 since the data is noisy
         
	final int K =Integer.parseInt(prop.getProperty("wvexpand.numnearest", "2"));
        final float thresh = Float.parseFloat(prop.getProperty("wvexpand.thresh", "0.6"));

        Terms terms = reader.getTermVector(docId, FIELD_BAG_OF_WORDS);
        if (terms == null || terms.size() == 0)
            return;

	int docLen = getDocLen(terms); // compute doc length
        if(docLen==0)
            return;
	List<GLMTermWeight1> tstats = computeNormalizedTf(terms, docLen); // normalize tfs

	List<GLMTermWeight1> docTransformWeights = computeDocTransformProbs(tstats); // compute GLM doc transformation

		// compute GLM coll transformation (array size expected to be higher due to expanded terms)
	List<GLMTermWeight1> collTransformWeights = computeCollTransformProb(docTransformWeights, K, thresh);

	writeDocToIndex(writer,doc,collTransformWeights);
  }
  
  public float collection_stat(String term) throws IOException
  {
    Term termInstance = new Term(FIELD_BAG_OF_WORDS,term);                              
    float termFreq = reader.totalTermFreq(termInstance);  //collection frequency of the given term is obatined
    Fields fields = MultiFields.getFields(reader);
    Terms tm = fields.terms(FIELD_BAG_OF_WORDS);
    float vocSize = tm.getSumTotalTermFreq();  //This is the total collection size for the entire vocabulary
    float val1 = (termFreq/vocSize);  // [collection frequency of term / total collection size] 
    return val1;     
  }
  
  public void writeDocToIndex(IndexWriter writer,Document doc,List<GLMTermWeight1> transformed_list) throws Exception
  {
 
        //The transformed List contains unique terms of the documents as well as the extended unique
        // terms of the collection . So this list will be just iterated and the fields will be added.
            
            int l=transformed_list.size();
            for(int i=0;i<l;i++)
            {
                Document d = new Document();
                d.add(new Field(FIELD_ID, doc.get(FIELD_ID), Field.Store.YES, Field.Index.NOT_ANALYZED));
                d.add(new Field(FIELD_TERM, transformed_list.get(i).term, Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
                // searchable by term
                
                float collection_info=collection_stat(transformed_list.get(i).term);
                
                //Divinding each of these weights by normalized collection frequency 
                // example [ntf_d/(normalized collection frequency)] = (ntf_d * collection size )/(term_frequency in collection)]
                
                
                transformed_list.get(i).ntf_d = transformed_list.get(i).ntf_d/collection_info;
                transformed_list.get(i).prob_in_doc=transformed_list.get(i).prob_in_doc/collection_info;
                transformed_list.get(i).prob_in_ng=transformed_list.get(i).prob_in_ng/collection_info;
                
                Probability ps = new Probability(transformed_list.get(i).ntf_d,transformed_list.get(i).prob_in_doc,transformed_list.get(i).prob_in_ng);
                // This class will create an object of Probability class which pipes the three weights together .
                
                
                d.add(new Field(FIELD_WEIGHT, ps.getProb(), Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO));
                //d.add(new Field(FIELD_GLM_DOC_TRANSFORM, tw.prob_in_doc, Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO));
                //d.add(new Field(FIELD_GLM_COLL_TRANSFORM, tw.prob_in_ng, Field.Store.YES, Field.Index.NOT_ANALYZED, Field.TermVector.NO));

                writer.addDocument(d);
            }

   }
  
  public static void main(String[] args) throws IOException, Exception {
        if (args.length == 0) 
        {
            args = new String[1];
            System.out.println("Usage: java GloveVecIndexer <prop-file>");
            args[0] = "/home/riya/IR/glm/tweet.index.properties";
        }

            SecondPassIndexNewGlove indexer = new SecondPassIndexNewGlove(args[0]);
            indexer.expandIndex();                
 
        
    }    
  
 }  




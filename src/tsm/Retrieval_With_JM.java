/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelIF;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.NormalizationH2;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import trec.TRECQuery;
import trec.TRECQueryParser;
import static tsm.SecondPassIndexNew.FIELD_ID;

/**
 *
 * @author riya
 */
public class Retrieval_With_JM 
{
    IndexReader reader;
    IndexSearcher searcher;
    Properties prop;   // retrieve.properties
    Properties iprop; // init.properties
    int numWanted;      // number of result to be retrieved
    HashMap<Integer, Float> docScorePredictionMap; 
    WordVecIndexer wvIndexer;
    String runName;     // name of the run
    float gamma,alpha, beta;   
    String stop_file;
    HashMap<String,Integer>  stop_map;
    
    public Retrieval_With_JM(String rpropFile) throws FileNotFoundException, IOException
    {
        prop = new Properties();
        prop.load(new FileReader(rpropFile));
        stop_file=prop.getProperty("stopfile");
        gamma= Float.parseFloat(prop.getProperty("retrieve.gamma"));
        alpha = Float.parseFloat(prop.getProperty("retrieve.alpha"));
        beta = Float.parseFloat(prop.getProperty("retrieve.beta"));
        stop_map=new HashMap<>();
        String wvIndex_dir = prop.getProperty("wv.index"); //This is the expanded index 
        String line;
        try {
            int count=1;
            FileReader fr = new FileReader(stop_file);
            BufferedReader br = new BufferedReader(fr);
            while ( (line = br.readLine()) != null ) {
                stop_map.put(line.trim(),count);
                count=count+1;
            }
            br.close(); fr.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
        System.out.println("Running queries against index: " + wvIndex_dir);
        try {
            File indexDir;
            indexDir = new File(wvIndex_dir);
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            searcher = new IndexSearcher(reader);
            setSimilarityFunction(2, (float) 0.5,0);
            numWanted = 2000;
            //Integer.parseInt(prop.getProperty("retrieve.num_wanted", "5000"));
            runName = prop.getProperty("retrieve.runname", "JM_word2vec");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    List<TRECQuery> constructQueries() throws Exception
    {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile);
        parser.parse();
        return parser.queries;
    }
    private void setSimilarityFunction(int choice, float param1, float param2)
    {

        switch(choice) 
        {
            case 0:
                searcher.setSimilarity(new DefaultSimilarity());
                System.out.println("Similarity function set to DefaultSimilarity");
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity(param1, param2));
                System.out.println("Similarity function set to BM25Similarity"
                    + " with parameters: " + param1 + " " + param2);
                break;
            case 2:
                searcher.setSimilarity(new LMJelinekMercerSimilarity(param1));
                System.out.println("Similarity function set to LMJelinekMercerSimilarity"
                    + " with parameter: " + param1);
                break;
            case 3:
                searcher.setSimilarity(new LMDirichletSimilarity(param1));
                System.out.println("Similarity function set to LMDirichletSimilarity"
                    + " with parameter: " + param1);
                break;
            case 4:
                searcher.setSimilarity(new DFRSimilarity(new BasicModelIF(), new AfterEffectB(), new NormalizationH2()));
                System.out.println("Similarity function set to DFRSimilarity with default parameters");
                break;
        }
    }
    public ScoreDoc[] retrieve(Query query) throws Exception 
        {

            ScoreDoc[] hits = null;  //Result of Per term query vector are stored 
            TopDocs topDocs = null;  

            TopScoreDocCollector collector = TopScoreDocCollector.create(numWanted,true);
            
            searcher.search(query,collector);
            topDocs = collector.topDocs();
            hits = topDocs.scoreDocs;
            if(hits == null)
                System.out.println("Nothing found");

            return hits;
        }
    
    public void retrieveAll() throws Exception
    {
 
            int i;
            ScoreDoc[] hits = null;
            String resultsFile = prop.getProperty("retrieve.wv_results_file");
            FileWriter fw = new FileWriter(resultsFile);
            
            
            List<TRECQuery> queries = constructQueries();
            for (TRECQuery query : queries) {
                //1 . only title
                //2 . tile+description
                //3 . title+description+narration
                //  analysing_query function has been passed 3
                System.out.println("Retrieving results for query: " + query.id);
                HashMap<String,Integer> strn = query.analysing_query_jm(new WhitespaceAnalyzer(Version.LUCENE_4_9),3, stop_map);
                BooleanQuery q = new BooleanQuery();
                for(String s : strn.keySet())
                {
                    Term thisTerm = new Term("words",s);
                    Query tq = new TermQuery(thisTerm);
                    q.add(tq, BooleanClause.Occur.SHOULD);
     
                }
                //System.out.println(q.toString());
                hits = retrieve(q);
                int hits_length = hits.length;
                System.out.println("No of docs retrieved for the given query "+hits_length);
                StringBuffer resBuffer = new StringBuffer();

                for (i = 0; i < hits_length;i++)
                {
                    int luceneDocId = hits[i].doc;
                    Document d = searcher.doc(luceneDocId);
                    resBuffer.append(query.id).append("\tQ0\t").append(d.get(FIELD_ID)).append("\t").append((i)).append("\t").append(hits[i].score).append("\t").append(runName).append("\n");                
                }
                fw.write(resBuffer.toString());
            
            }
        fw.close();
                
                
    }
    
    public static void main(String[] args)
    {
        if (args.length < 2) {
            args = new String[2];
            //args[0] ="/home/riya/IR/glm/tweet.index.properties";
            args[0] ="/home/riya/IR/glm/tweet.retrieve.properties";
        }
        
        try {
            
            Retrieval_With_JM searcher = new Retrieval_With_JM(args[0]);
            searcher.retrieveAll();
        }
        
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
}

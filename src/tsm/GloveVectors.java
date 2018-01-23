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
import java.io.IOException;
import java.io.PrintWriter;
import static java.lang.Character.isLetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import static tsm.GloveVectors.singleTon;

/**
 *
 * @author riya
 */
public class GloveVectors {
    
    Properties prop;
    int k;
    HashMap<String, GloveVec> wordvecmap;
    HashMap<String, List<GloveVec>> nearestGloveVectorsMap; // Store the pre-computed NNs after read from file
    static GloveVectors singleTon;
    
    public GloveVectors(String propFile) throws Exception {
        
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        //distList = new ArrayList<>(wordvecmap.size());
        k = Integer.parseInt(prop.getProperty("wordvecs.numnearest", "20"));        
        String wordvecFile = prop.getProperty("glovevecs.vecfile");

        wordvecmap = new HashMap();
        try (FileReader fr = new FileReader(wordvecFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                GloveVec wv = new GloveVec(line);
                if(isLegalToken(wv.word))
                    wordvecmap.put(wv.word, wv);
            }
        }
    }
    static public GloveVectors createInstance(Properties prop) throws Exception {
        if(singleTon == null) {
            singleTon = new GloveVectors(prop);
            singleTon.loadPrecomputedNNs();
            System.out.println("Precomputed NNs loaded");
        }
        return singleTon;
    }
    
    public GloveVectors(Properties prop) throws Exception { 
        this.prop = prop;
        //distList = new ArrayList<>(wordvecmap.size());
        k = Integer.parseInt(prop.getProperty("wordvecs.numnearest", "25"));        
        String wordvecFile = prop.getProperty("glovevecs.vecfile");
        
        if (wordvecFile == null)
            return;
        
        wordvecmap = new HashMap();
        try (FileReader fr = new FileReader(wordvecFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                GloveVec wv = new GloveVec(line);
                if(isLegalToken(wv.word))
                    wordvecmap.put(wv.word, wv);
            }
        }
    }
    
    public void computeAndStoreNNs() throws FileNotFoundException {
        String NNDumpPath = prop.getProperty("NNDumpPath_glove");
        if(NNDumpPath!=null) {
            File f = new File(NNDumpPath);
        }
        else {
            System.out.println("Null found");
            return;
        }

        System.out.println("Dumping the NN in: "+ NNDumpPath);
        PrintWriter pout = new PrintWriter(NNDumpPath);

        System.out.println("Precomputing NNs for each word");

        for (Map.Entry<String, GloveVec> entry : wordvecmap.entrySet()) {
            GloveVec wv = entry.getValue();
            if (isLegalToken(wv.word)) { 
                System.out.println("Precomputing NNs for " + wv.word);
                List<GloveVec> nns = getNearestNeighbors(wv.word);
                if (nns != null) {
                    pout.print(wv.word + "\t");
                    for (int i = 0; i < nns.size(); i++) {
                        GloveVec nn = nns.get(i);
                        pout.print(nn.word + ":" + nn.querySim + "\t");
                    }
                    pout.print("\n");
                }
            }
        }
        pout.close();
    }
    public List<GloveVec> getPrecomputedNNs(String queryWord) {
        return nearestGloveVectorsMap.get(queryWord);
    }
    public List<GloveVec> getPrecomputedNNs(String queryWord, int k, float thresh) {
        List<GloveVec> nnlist = nearestGloveVectorsMap.get(queryWord);
        if (nnlist == null)
            return null; 
        int kprime = Math.min(k, nnlist.size());
        List<GloveVec> sublist = nnlist.subList(0, kprime);
        //List<GloveVec> filteredList = new ArrayList<>(kprime);
       /* for (GloveVec wv : sublist) {
            if (wv.querySim > thresh)
                filteredList.add(wv);
        } */
        return sublist;
    }
    
    public List<GloveVec> getNearestNeighbors(String queryWord) {
        ArrayList<GloveVec> distList = new ArrayList<>(wordvecmap.size());
        
        GloveVec queryVec = wordvecmap.get(queryWord);
        if (queryVec == null)
            return null;
        
        for (Map.Entry<String, GloveVec> entry : wordvecmap.entrySet()) {
            GloveVec wv = entry.getValue();
            if (wv.word.equals(queryWord))
                continue;
            wv.querySim = queryVec.cosineSim(wv);
            distList.add(wv);
        }
        Collections.sort(distList);
        return distList.subList(0, Math.min(k, distList.size()));        
    }
    public float getSim(String u, String v) {
        GloveVec uVec = wordvecmap.get(u);
        GloveVec vVec = wordvecmap.get(v);
        if (uVec == null || vVec == null) {
            return 0;
        }

        return uVec.cosineSim(vVec);
    }
    
    private boolean isLegalToken(String word) {
        boolean flag = true;
        for (int i=0; i< word.length(); i++) {
            if(!isLetter(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    public void loadPrecomputedNNs() throws FileNotFoundException, IOException {
        nearestGloveVectorsMap = new HashMap<>();
        String NNDumpPath = prop.getProperty("NNDumpPath");
        if (NNDumpPath == null) {
            System.out.println("NNDumpPath not specified in configuration...");
            return;
        }
        System.out.println("Reading from the NN dump at: "+ NNDumpPath);
        File NNDumpFile = new File(NNDumpPath);
        
        try (FileReader fr = new FileReader(NNDumpFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, " \t:");
                List<String> tokens = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    tokens.add(token);
                }
                List<GloveVec> nns = new LinkedList();
                int len = tokens.size();
                for (int i=1; i < len-1; i+=2) {
                    nns.add(new GloveVec(tokens.get(i), Float.parseFloat(tokens.get(i+1))));
                }
                nearestGloveVectorsMap.put(tokens.get(0), nns);
            }
            System.out.println("NN dump has been reloaded");
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            args[0] = "/home/riya/IR/glm/tweet.index.properties";
        }
        String str="/home/riya/IR/glm/tweet.index.properties";
        try {
            
            GloveVectors qe = new GloveVectors(args[0]);
            qe.computeAndStoreNNs();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

    
    
    
   

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import org.apache.lucene.util.BytesRef;



public class GloveVec implements Comparable<GloveVec> 
{
    String word;
    float[] vec;
    float norm=0;
    float querySim; // distance from a reference query point
    
    GloveVec(String word, float[] vec) 
    {
        this.word = word;
        this.vec = vec;
    }
    
    GloveVec(String word, float querySim) {
        this.word = word;
        this.querySim = querySim;
    }
    
    public boolean isAlpha(String name) 
    {
        return name.matches("[a-z]+");
    }
    
    GloveVec(String line) {
        String[] tokens = line.split("\\s+");
        word = tokens[0];
        word= word.replaceAll("[<]","");
        word=word.replaceAll("[>]",line);
        
        //System.out.println(word);
        
        if(!isAlpha(word))
            return;
           
        vec = new float[tokens.length-1];
        for (int i = 1; i < tokens.length; i++)
        {
            vec[i-1] = Float.parseFloat(tokens[i]);
        
            //System.out.println(vec[i-1]+" ");
        }
        
        norm = getNorm();
    }
    
    float getNorm() {
        //if (norm == 0) {
            // calculate and store
            float sum = 0;
            for (int i = 0; i < vec.length; i++) {
                sum += vec[i]*vec[i];
            //}
            norm = (float)Math.sqrt(sum);
        }
        return norm;
    }
    
    float cosineSim(GloveVec that) {
        float sum = 0;
        for (int i = 0; i < this.vec.length; i++) {
            if (that == null) {
                return 0;
            }
            sum += vec[i] * that.vec[i];
        }
        return sum / (this.norm*that.norm);
    }

    @Override
    public int compareTo(GloveVec that) {
        return this.querySim > that.querySim? -1 : this.querySim == that.querySim? 0 : 1;
    }
    
    byte[] getBytes() throws IOException {
        byte[] byteArray;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutput out;
            out = new ObjectOutputStream(bos);
            out.writeObject(this);
            byteArray = bos.toByteArray();
            out.close();
        }
        return byteArray;
    }
    
    static GloveVec decodeFromByteArray(BytesRef bytes) throws Exception {
        ObjectInput in;
        Object o;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes.bytes)) {
            in = new ObjectInputStream(bis);
            o = in.readObject();
        }
        in.close();
        return (GloveVec)o;
    }

    
}

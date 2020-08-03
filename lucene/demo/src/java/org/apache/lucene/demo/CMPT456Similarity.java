/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.demo;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.util.BytesRef;

public final class CMPT456Similarity extends ClassicSimilarity {
  
  /**
   * Sole constructor. (For invocation by subclass 
   * constructors, typically implicit.)
   */
  public CMPT456Similarity() {
    super();
  }

  
  /** Computes a score factor based on a term or phrase's frequency in a
   * document.  This value is multiplied by the {@link #idf(long, long)}
   * factor for each term in the query and these products are then summed to
   * form the initial score for a document.
   *
   * <p>Terms and phrases repeated in a document indicate the topic of the
   * document, so implementations of this method usually return larger values
   * when <code>freq</code> is large, and smaller values when <code>freq</code>
   * is small.
   *
   * @param freq the frequency of a term within a document
   * @return a score factor based on a term's within-document frequency
   */
  /** Implemented as <code>sqrt(freq)</code>. */
  @Override
  public float tf(float freq) {
    return (float)Math.sqrt(1+freq);
  }

  /**
   * Computes a score factor for a simple term and returns an explanation
   * for that score factor.
   * 
   * <p>
   * The default implementation uses:
   * 
   * <pre class="prettyprint">
   * idf(docFreq, docCount);
   * </pre>
   * 
   * Note that {@link CollectionStatistics#docCount()} is used instead of
   * {@link org.apache.lucene.index.IndexReader#numDocs() IndexReader#numDocs()} because also 
   * {@link TermStatistics#docFreq()} is used, and when the latter 
   * is inaccurate, so is {@link CollectionStatistics#docCount()}, and in the same direction.
   * In addition, {@link CollectionStatistics#docCount()} does not skew when fields are sparse.
   *   
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explain object that includes both an idf score factor 
             and an explanation for the term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(idf, "idf(docFreq=" + df + ", docCount=" + docCount + ")");
  }

  /**
   * Computes a score factor for a phrase.
   * 
   * <p>
   * The default implementation sums the idf factor for
   * each term in the phrase.
   * 
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the terms in the phrase
   * @return an Explain object that includes both an idf 
   *         score factor for the phrase and an explanation 
   *         for each term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    double idf = 0d; // sum into a double before casting into a float
    List<Explanation> subs = new ArrayList<>();
    for (final TermStatistics stat : termStats ) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      subs.add(idfExplain);
      idf += idfExplain.getValue();
    }
    return Explanation.match((float) idf, "idf(), sum of:", subs);
  }

  /** Computes a score factor based on a term's document frequency (the number
   * of documents which contain the term).  This value is multiplied by the
   * {@link #tf(float)} factor for each term in the query and these products are
   * then summed to form the initial score for a document.
   *
   * <p>Terms that occur in fewer documents are better indicators of topic, so
   * implementations of this method usually return larger values for rare terms,
   * and smaller values for common terms.
   *
   * @param docFreq the number of documents which contain the term
   * @param docCount the total number of documents in the collection
   * @return a score factor based on the term's document frequency
   */
  /** Implemented as <code>log((docCount+1)/(docFreq+1)) + 1</code>. */
  @Override
  public float idf(long docFreq, long docCount) {
    return (float)(Math.log((docCount+2)/(double)(docFreq+2)) + 1.0);
  }



  private final class TFIDFSimScorer extends SimScorer {
    private final IDFStats stats;
    private final float weightValue;
    private final NumericDocValues norms;
    
    TFIDFSimScorer(IDFStats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.value;
      this.norms = norms;
    }
    
    @Override
    public float score(int doc, float freq) {
      final float raw = tf(freq) * weightValue; // compute tf(f)*weight
      
      return norms == null ? raw : raw * decodeNormValue(norms.get(doc));  // normalize for field
    }
    
    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }

    @Override
    public Explanation explain(int doc, Explanation freq) {
      return explainScore(doc, freq, stats, norms);
    }
  }
  
  /** Collection statistics for the TF-IDF model. The only statistic of interest
   * to this model is idf. */
  private static class IDFStats extends SimWeight {
    private final String field;
    /** The idf and its explanation */
    private final Explanation idf;
    private float queryNorm;
    private float boost;
    private float queryWeight;
    private float value;
    
    public IDFStats(String field, Explanation idf) {
      // TODO: Validate?
      this.field = field;
      this.idf = idf;
      normalize(1f, 1f);
    }

    @Override
    public float getValueForNormalization() {
      // TODO: (sorta LUCENE-1907) make non-static class and expose this squaring via a nice method to subclasses?
      return queryWeight * queryWeight;  // sum of squared weights
    }

    @Override
    public void normalize(float queryNorm, float boost) {
      this.boost = boost;
      this.queryNorm = queryNorm;
      queryWeight = queryNorm * boost * idf.getValue();
      value = queryWeight * idf.getValue();         // idf for document
    }
  }  

  private Explanation explainQuery(IDFStats stats) {
    List<Explanation> subs = new ArrayList<>();

    Explanation boostExpl = Explanation.match(stats.boost, "boost");
    if (stats.boost != 1.0f)
      subs.add(boostExpl);
    subs.add(stats.idf);

    Explanation queryNormExpl = Explanation.match(stats.queryNorm,"queryNorm");
    subs.add(queryNormExpl);

    return Explanation.match(
        boostExpl.getValue() * stats.idf.getValue() * queryNormExpl.getValue(),
        "queryWeight, product of:", subs);
  }

  private Explanation explainField(int doc, Explanation freq, IDFStats stats, NumericDocValues norms) {
    Explanation tfExplanation = Explanation.match(tf(freq.getValue()), "tf(freq="+freq.getValue()+"), with freq of:", freq);
    Explanation fieldNormExpl = Explanation.match(
        norms != null ? decodeNormValue(norms.get(doc)) : 1.0f,
        "fieldNorm(doc=" + doc + ")");

    return Explanation.match(
        tfExplanation.getValue() * stats.idf.getValue() * fieldNormExpl.getValue(),
        "fieldWeight in " + doc + ", product of:",
        tfExplanation, stats.idf, fieldNormExpl);
  }

  private Explanation explainScore(int doc, Explanation freq, IDFStats stats, NumericDocValues norms) {
    Explanation queryExpl = explainQuery(stats);
    Explanation fieldExpl = explainField(doc, freq, stats, norms);
    if (queryExpl.getValue() == 1f) {
      return fieldExpl;
    }
    return Explanation.match(
        queryExpl.getValue() * fieldExpl.getValue(),
        "score(doc="+doc+",freq="+freq.getValue()+"), product of:",
        queryExpl, fieldExpl);
  }
}

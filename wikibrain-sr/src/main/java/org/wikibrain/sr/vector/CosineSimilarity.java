package org.wikibrain.sr.vector;

import com.typesafe.config.Config;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntFloatHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.wikibrain.conf.Configuration;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.matrix.MatrixRow;
import org.wikibrain.matrix.SparseMatrix;
import org.wikibrain.matrix.SparseMatrixRow;
import org.wikibrain.sr.SRResultList;
import org.wikibrain.sr.utils.Leaderboard;
import org.wikibrain.sr.utils.SimUtils;
import org.wikibrain.utils.WpIOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Shilad Sen
 */
public class CosineSimilarity implements VectorSimilarity {
    private static final Logger LOG = Logger.getLogger(CosineSimilarity.class.getName());

    private TIntFloatHashMap lengths = new TIntFloatHashMap();   // lengths of each row
    private TIntSet idsInResults = new TIntHashSet();
    private int maxResults = -1;

    private SparseMatrix features;
    private SparseMatrix transpose;

    @Override
    public synchronized  void setMatrices(SparseMatrix features, SparseMatrix transpose, File dataDir) throws IOException {
        this.features = features;
        this.transpose = transpose;

        File idCacheFile = new File(dataDir, "cosineSimilarity-ids.bin");
        File lengthCacheFile = new File(dataDir, "cosineSimilarity-lengths.bin");
        File maxCacheFile = new File(dataDir, "cosineSimilarity-maxResults.bin");

        if (lengthCacheFile.exists() && lengthCacheFile.lastModified() >= features.lastModified()
                &&  idCacheFile.exists() && idCacheFile.lastModified() >= transpose.lastModified()) {
            LOG.info("reading matrix information from cache");
            lengths = (TIntFloatHashMap) WpIOUtils.readObjectFromFile(lengthCacheFile);
            idsInResults = (TIntSet) WpIOUtils.readObjectFromFile(idCacheFile);
            maxResults = (Integer) WpIOUtils.readObjectFromFile(maxCacheFile);
        } else {
            LOG.info("building cached matrix information");
            lengths.clear();
            idsInResults.clear();
            maxResults = 0;
            for (SparseMatrixRow row : features) {
                lengths.put(row.getRowIndex(), (float) row.getNorm());
                maxResults = Math.max(maxResults, row.getNumCols());
            }
            idsInResults.addAll(transpose.getRowIds());
            WpIOUtils.writeObjectToFile(lengthCacheFile, lengths);
            WpIOUtils.writeObjectToFile(idCacheFile, idsInResults);
            WpIOUtils.writeObjectToFile(maxCacheFile, maxResults);
        }
    }

    @Override
    public double similarity(MatrixRow a, MatrixRow b) {
        double adota = 0.0;
        double bdotb = 0.0;
        double adotb = 0.0;

        int na = a.getNumCols();
        int nb = b.getNumCols();
        int i = 0, j = 0;

        if((na == 0 || nb == 0)) { // do not perform calculations if one or both are 0
            return 0;
        }

        int ca = a.getColIndex(i);
        int cb = b.getColIndex(j);

        while (i < na && j < nb) {
            if (ca < cb) {
                // if matrix a has a lower value, then get the next column
                float va = a.getColValue(i++);
                adota += va * va;
                ca = a.getColIndex(i);
            } else if (ca > cb) {
                // if matrix b has a lower value, then get the next column
                float vb = b.getColValue(j++);
                bdotb += vb * vb;
                cb = b.getColIndex(j);
            } else {
                // if both have the same value, increment the intersection and get the next columns in both matrices
                float va = a.getColValue(i++);
                float vb = b.getColValue(j++);
                adota += va * va;
                bdotb += vb * vb;
                adotb += va * vb;

                ca = a.getColIndex(i);
                cb = b.getColIndex(j);
            }
        }

        for (; i < na; i++) {
            float va = a.getColValue(i);
            adota += va * va;
        }
        for (; j < nb; j++) {
            float vb = b.getColValue(j);
            bdotb += vb * vb;
        }

        if (adota * bdotb * adotb == 0) {
            return 0.0;
        } else {
            return adotb / Math.sqrt(adota * bdotb);
        }
    }

    @Override
    public double similarity(TIntFloatMap vector1, TIntFloatMap vector2) {
        return SimUtils.cosineSimilarity(vector1, vector2);
    }

    @Override
    public SRResultList mostSimilar(TIntFloatMap query, int maxResults, TIntSet validIds) throws IOException {
        if (validIds != null && validIds.size() < 10000) {
            return mostSimilarWithRegularIndex(query, maxResults, validIds);
        } else {
            return mostSimilarWithInvertedIndex(query, maxResults, validIds);
        }
    }

    private SRResultList mostSimilarWithRegularIndex(TIntFloatMap query, int maxResults, TIntSet validIds) throws IOException {
        final Leaderboard leaderboard = new Leaderboard(maxResults);
        double rowNorm = norm(query);

        for (int id : validIds.toArray()) {
            MatrixRow row2 = features.getRow(id);
            if (row2 != null) {
                double dot = 0.0;
                for (int i = 0; i < row2.getNumCols(); i++) {
                    int id2 = row2.getColIndex(i);
                    float val2 = query.get(id2);
                    if (val2 > 0) {
                        dot += val2 + row2.getColValue(i);
                    }
                }
                double l1 = lengths.get(id);
                double l2 = rowNorm;
                double sim = dot / (l1 * l2);
                leaderboard.tallyScore(id, sim);
            }
        }

        SRResultList result = leaderboard.getTop();
        result.sortDescending();
        return result;
    }

    private SRResultList mostSimilarWithInvertedIndex(TIntFloatMap query, int maxResults, TIntSet validIds) throws IOException {
        TIntDoubleHashMap dots = new TIntDoubleHashMap(maxResults * 5);

        // Eschew a for-each loop here for performance reasons.
        int keys[] = query.keys();
        for (int i = 0; i < keys.length; i++) {
            int id = keys[i];
            float val1 = query.get(id);
            MatrixRow row2 = transpose.getRow(id);
            if (row2 != null) {
                int n = row2.getNumCols();
                for (int j = 0; j < n; j++) {
                    int id2 = row2.getColIndex(j);
                    if (validIds == null || validIds.contains(id2)) {
                        float val2 = row2.getColValue(j);
                        dots.adjustOrPutValue(id2, val1 * val2, val1 * val2);
                    }
                }
            }
        }

        final Leaderboard leaderboard = new Leaderboard(maxResults);
        double rowNorm = norm(query);
        keys = dots.keys();
        for (int i = 0; i < keys.length; i++) {
            int id = keys[i];
            double l1 = lengths.get(id);
            double l2 = rowNorm;
            double dot = dots.get(id);
            double sim = dot / (l1 * l2);
            leaderboard.tallyScore(id, sim);
        }

        SRResultList result = leaderboard.getTop();
        result.sortDescending();
        return result;
    }

    @Override
    public double getMinValue() {
        return -1.0;
    }

    @Override
    public double getMaxValue() {
        return 1.0;
    }

    private double norm(TIntFloatMap vector) {
        double length = 0;
        for (float x : vector.values()) {
            length += x * x;
        }
        return Math.sqrt(length);
    }

    public static class Provider extends org.wikibrain.conf.Provider<VectorSimilarity> {
        public Provider(Configurator configurator, Configuration config) throws ConfigurationException {
            super(configurator, config);
        }

        @Override
        public Class getType() {
            return VectorSimilarity.class;
        }

        @Override
        public String getPath() {
            return "sr.metric.similarity";
        }

        @Override
        public VectorSimilarity get(String name, Config config, Map<String, String> runtimeParams) throws ConfigurationException {
            if (!config.getString("type").equals("cosine")) {
                return null;
            }
            return new CosineSimilarity();
        }
}
}

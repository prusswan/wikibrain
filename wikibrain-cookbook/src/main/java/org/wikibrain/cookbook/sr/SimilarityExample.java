package org.wikibrain.cookbook.sr;

import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.sr.*;
import org.wikibrain.sr.utils.ExplanationFormatter;

/**
 * @author Shilad Sen
 */
public class SimilarityExample {

    public static void main(String args[]) throws ConfigurationException, DaoException {
        // Initialize the WikiBrain environment and get the local page dao
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        LocalPageDao lpDao = conf.get(LocalPageDao.class);
        Language simple = Language.getByLangCode("simple");

        // Retrieve the "ensemble" sr metric for simple english
        SRMetric sr = conf.get(
                SRMetric.class, "ensemble",
                "language", simple.getLangCode());

        //Similarity between strings
        String pairs[][] = new String[][] {
                { "cat", "kitty" },
                { "obama", "president" },
                { "tires", "car" },
                { "java", "computer" },
                { "dog", "computer" },
        };

        ExplanationFormatter formatter= new ExplanationFormatter(lpDao);
        for (String pair[] : pairs) {
                SRResult s = sr.similarity(pair[0], pair[1], true);
            System.out.println(s.getScore() + ": '" + pair[0] + "', '" + pair[1] + "'");
            for (Explanation e:s.getExplanations()) {
                System.out.println(formatter.formatExplanation(e));
            }
        }
    }
}

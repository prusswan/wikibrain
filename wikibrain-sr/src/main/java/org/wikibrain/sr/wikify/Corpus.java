package org.wikibrain.sr.wikify;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.wikibrain.conf.Configuration;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.RawPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.phrases.AnchorTextPhraseAnalyzer;
import org.wikibrain.phrases.LinkProbabilityDao;
import org.wikibrain.phrases.PhraseAnalyzer;
import org.wikibrain.phrases.PhraseAnalyzerDao;
import org.wikibrain.sr.word2vec.Word2Phrase;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Shilad Sen
 */
public class Corpus {
    private final Language language;
    private final File directory;
    private final Wikifier wikifer;
    private final RawPageDao rawPageDao;
    private final LocalPageDao localPageDao;
    private final PhraseAnalyzerDao phraseAnalyzerDao;
    private final LinkProbabilityDao linkProbabilityDao;

    public Corpus(Language language, File directory, Wikifier wikifer, RawPageDao rawPageDao, LocalPageDao localPageDao, PhraseAnalyzerDao phraseAnalyzerDao, LinkProbabilityDao linkProbabilityDao) {
        this.language = language;
        this.directory = directory;
        this.wikifer = wikifer;
        this.rawPageDao = rawPageDao;
        this.localPageDao = localPageDao;
        this.phraseAnalyzerDao = phraseAnalyzerDao;
        this.linkProbabilityDao = linkProbabilityDao;
    }

    public File getDirectory() {
        return directory;
    }

    public void create() throws IOException, DaoException {
        FileUtils.deleteQuietly(directory);
        directory.mkdirs();

        WikiTextCorpusCreator creator = new WikiTextCorpusCreator(language, wikifer, rawPageDao, localPageDao, linkProbabilityDao);
        creator.write(directory);
    }

    public File getCorpusFile() {
        return new File(directory, "corpus.txt");
    }

    public File getDictionaryFile() {
        return new File(directory, "dictionary.txt");
    }

    public boolean exists() {
        return getCorpusFile().isFile() && getDictionaryFile().isFile();
    }

    public static class Provider extends org.wikibrain.conf.Provider<Corpus> {

        public Provider(Configurator configurator, Configuration config) throws ConfigurationException {
            super(configurator, config);
        }

        @Override
        public Class<Corpus> getType() {
            return Corpus.class;
        }

        @Override
        public String getPath() {
            return "sr.corpus";
        }

        @Override
        public Corpus get(String name, Config config, Map<String, String> runtimeParams) throws ConfigurationException {
            if (runtimeParams == null || !runtimeParams.containsKey("language")) {
                throw new IllegalArgumentException("Corpus requires 'language' runtime parameter");
            }
            Language lang = Language.getByLangCode(runtimeParams.get("language"));
            Configurator c = getConfigurator();
            Wikifier wikifier = c.get(Wikifier.class, "default", "language", lang.getLangCode());
            AnchorTextPhraseAnalyzer phraseAnalyzer = (AnchorTextPhraseAnalyzer)c.get(
                    PhraseAnalyzer.class, config.getString("phraseAnalyzer"));
            PhraseAnalyzerDao paDao = phraseAnalyzer.getDao();
            LinkProbabilityDao linkProbabilityDao = c.get(LinkProbabilityDao.class);

            return new Corpus(
                    lang,
                    new File(config.getString("path"), lang.getLangCode()),
                    wikifier,
                    c.get(RawPageDao.class, config.getString("rawPageDao")),
                    c.get(LocalPageDao.class, config.getString("localPageDao")),
                    paDao,
                    linkProbabilityDao
            );
        }
    }
}

package org.wikapidia.lucene.tokenizers;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.it.ItalianLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.ElisionFilter;
import org.apache.lucene.util.Version;
import org.wikapidia.core.lang.Language;
import org.wikapidia.lucene.LuceneException;
import org.wikapidia.lucene.TokenizerOptions;

import java.util.Arrays;

/**
 * @author Ari Weiland
 */
public class ItalianTokenizer extends LanguageTokenizer {

    private final CharArraySet DEFAULT_ARTICLES = CharArraySet.unmodifiableSet(
            new CharArraySet(matchVersion, Arrays.asList(
                    "c", "l", "all", "dall", "dell", "nell", "sull", "coll", "pell",
                    "gl", "agl", "dagl", "degl", "negl", "sugl", "un", "m", "t", "s", "v", "d"
            ), true));

    protected ItalianTokenizer(Version version, TokenizerOptions options, Language language) {
        super(version, options, language);
    }

    @Override
    public TokenStream getTokenStream(TokenStream input, CharArraySet stemExclusionSet) throws LuceneException {

        TokenStream stream = new StandardFilter(matchVersion, input);
        if (caseInsensitive)
            stream = new LowerCaseFilter(matchVersion, stream);
        if (useStopWords) {
            stream = new ElisionFilter(stream, DEFAULT_ARTICLES);
            stream = new StopFilter(matchVersion, stream, ItalianAnalyzer.getDefaultStopSet());
        }
        if (useStem) {
            if (!stemExclusionSet.isEmpty())
                stream = new SetKeywordMarkerFilter(stream, stemExclusionSet);
            stream = new ItalianLightStemFilter(stream);
        }
        return stream;
    }
}

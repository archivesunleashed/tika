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
package org.apache.tika.eval.textstats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.tika.eval.langid.Language;
import org.apache.tika.eval.langid.LanguageIDWrapper;
import org.apache.tika.eval.tokens.TokenCounts;


public class TextStatsCalculator {

    private static final String FIELD = "f";
    private final Analyzer analyzer;
    private final LanguageIDWrapper languageIDWrapper;
    private final List<LanguageAwareTokenCountStats> languageAwareTokenCountStats = new ArrayList<>();
    private final List<TokenCountStats> tokenCountStats = new ArrayList<>();
    private final List<StringStats> stringStats = new ArrayList<>();

    public TextStatsCalculator(List<TextStats> calculators) {
        this(calculators, null, null);
    }

    public TextStatsCalculator(List<TextStats> calculators, Analyzer analyzer,
                               LanguageIDWrapper languageIDWrapper) {
        this.analyzer = analyzer;
        this.languageIDWrapper = languageIDWrapper;
        for (TextStats t : calculators) {
            if (t instanceof StringStats) {
                stringStats.add((StringStats)t);
            } else if (t instanceof LanguageAwareTokenCountStats) {
                languageAwareTokenCountStats.add((LanguageAwareTokenCountStats) t);
                if (languageIDWrapper == null) {
                    throw new IllegalArgumentException("Must specify a LanguageIdWrapper "+
                            "if you want to calculate languageAware stats: "+t.getClass());
                }
            } else if (t instanceof TokenCountStats) {
                tokenCountStats.add((TokenCountStats)t);
                if (analyzer == null) {
                    throw new IllegalArgumentException(
                            "Analyzer must not be null if you are using "+
                                    "a TokenCountStats: "+t.getClass()
                    );
                }
            } else {
                throw new IllegalArgumentException(
                        "I regret I don't yet handle: "+t.getClass()
                );
            }
        }
    }

    public Map<Class, Object> calculate(String txt) {
        Map<Class, Object> results = new HashMap<>();
        for (StringStats calc : stringStats) {
            results.put(calc.getClass(), calc.compute(txt));
        }

        TokenCounts tokenCounts = null;
        if (tokenCountStats.size() > 0 || languageAwareTokenCountStats.size() > 0) {
            try {
                tokenCounts = tokenize(txt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (languageAwareTokenCountStats.size() > 0) {
            List<Language> langs = results.containsKey(LanguageIDWrapper.class) ?
                    (List)results.get(LanguageIDWrapper.class) : languageIDWrapper.compute(txt);
            results.put(LanguageIDWrapper.class, langs);
            for (LanguageAwareTokenCountStats calc : languageAwareTokenCountStats) {
                results.put(calc.getClass(), calc.compute(langs, tokenCounts));
            }
        }

        for (TokenCountStats calc : tokenCountStats) {
            results.put(calc.getClass(), calc.compute(tokenCounts));
        }
        return results;
    }

    private TokenCounts tokenize(String txt) throws IOException  {
        TokenCounts counts = new TokenCounts();
        TokenStream ts = analyzer.tokenStream(FIELD, txt);
        try {
            CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String token = termAtt.toString();
                counts.increment(token);
            }
        } finally {
            ts.close();
            ts.end();
        }
        return counts;
    }
}

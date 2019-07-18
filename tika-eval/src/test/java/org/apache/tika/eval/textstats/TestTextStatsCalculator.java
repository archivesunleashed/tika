package org.apache.tika.eval.textstats;


import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.lucene.analysis.Analyzer;
import org.apache.tika.eval.langid.LanguageIDWrapper;
import org.apache.tika.eval.tokens.AnalyzerManager;
import org.apache.tika.eval.tokens.CommonTokenCountManager;
import org.apache.tika.eval.tokens.CommonTokenResult;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestTextStatsCalculator {

    private static Analyzer ANALYZER;
    private static CommonTokenCountManager COMMON_TOKENS;
    @BeforeClass
    public static void setUp() throws Exception {
        ANALYZER = AnalyzerManager.newInstance(10000000).getGeneralAnalyzer();
        COMMON_TOKENS = new CommonTokenCountManager(null, null);
        LanguageIDWrapper.loadBuiltInModels();
    }

    @Test
    public void testKL() throws Exception {
        List<TextStats> stats = new ArrayList<>();
        stats.add(new CommonTokensKLDivergence(COMMON_TOKENS));
        TextStatsCalculator calc = new TextStatsCalculator(stats, ANALYZER,
                new LanguageIDWrapper());
        String txt = "qwertyuiopsdflkasjf;laskjfada asdfasd asdfa fasdf sdfasdfas ";
        Map<Class, Object> results = calc.calculate(txt);
        System.out.println(results);
    }

    @Test
    public void testOneOff() throws Exception {
        Path p = Paths.get(//fill in here);
        List<String> lines = FileUtils.readLines(p.toFile(), StandardCharsets.UTF_8);
        List<TextStats> stats = new ArrayList<>();
        stats.add(new CommonTokensKLDivergence(COMMON_TOKENS));
        stats.add(new CommonTokensKLDNormed(COMMON_TOKENS));
        stats.add(new CommonTokensCosine(COMMON_TOKENS));
        stats.add(new CommonTokens(COMMON_TOKENS));
        stats.add(new CommonTokensHellinger(COMMON_TOKENS));
        stats.add(new CommonTokensBhattacharyya(COMMON_TOKENS));
        TextStatsCalculator calc = new TextStatsCalculator(stats, ANALYZER,
                new LanguageIDWrapper());
        System.out.println("Chars\ttokens\tkld\tkld_normed\tcosine\thellinger\tbhattacharyya\toov");
        DecimalFormat df = new DecimalFormat("#.###");
        for (int i = 100; i <= 100000; i += 100) {
            Map<Object, SummaryStatistics> map = new HashMap<>();
            for (int j = 0; j < 30; j++) {
                Collections.shuffle(lines);
                Map<Class, Object> results = calc.calculate(substring(lines, i));
                CommonTokenResult commonTokenResult = ((CommonTokenResult) results.get(CommonTokens.class));
                for (TextStats textStats : stats) {
                    SummaryStatistics summaryStatistics = map.get(textStats.getClass());
                    if (summaryStatistics == null) {
                        summaryStatistics = new SummaryStatistics();
                        map.put(textStats.getClass(), summaryStatistics);
                    }
                    if (textStats instanceof CommonTokens) {
                        summaryStatistics.addValue(
                                ((CommonTokenResult)results.get(textStats.getClass())).getOOV());
                    } else {
                        summaryStatistics.addValue((double) results.get(textStats.getClass()));
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append(i).append("\t");
            for (TextStats textStats :stats) {
                sb.append(df.format(map.get(textStats.getClass()).getMean())).append("\t");
                sb.append(
                        df.format(
                                map.get(textStats.getClass()).getStandardDeviation())).append("\t");
            }
            System.out.println(sb.toString());
        }
/*
        for (int i = 2000; i <= txt.length(); i += 1000) {
            Map<Class, Object> results = calc.calculate(txt.substring(0, i));
            CommonTokenResult commonTokenResult = ((CommonTokenResult)results.get(CommonTokens.class));
            System.out.println(i + "\t" + commonTokenResult.getAlphabeticTokens() +
                    "\t" + results.get(CommonTokensKLDivergence.class) +
                    "\t" + results.get(CommonTokensKLDNormed.class) +
                    "\t" + results.get(CommonTokensCosine.class) +
                    "\t" + results.get(CommonTokensHellinger.class) +
                    "\t" + results.get(CommonTokensBhattacharyya.class)
                    + "\t"+commonTokenResult.getOOV());
        }*/
    }

    private String substring(List<String> lines, int length) {
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (sb.length() > length) {
                sb.substring(0, length);
                return sb.toString();
            }
            sb.append(" ").append(line);
        }
        return sb.toString();
    }
}

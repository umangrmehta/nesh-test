package com.umang.neshtest.corenlp;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;

public class NLPPipeline {
	private static final List<Integer> sentimentScoreDictionary = new ArrayList<>();
	private static final List<String> sentimentLabelDictionary = new ArrayList<>();
	private static StanfordCoreNLP sentimentPipeline;
	private static StanfordCoreNLP openIEPipeline;

	static
	{
		sentimentScoreDictionary.add(-2);
		sentimentScoreDictionary.add(-1);
		sentimentScoreDictionary.add(0);
		sentimentScoreDictionary.add(1);
		sentimentScoreDictionary.add(2);

		sentimentLabelDictionary.add("Strongly Negative");
		sentimentLabelDictionary.add("Negative");
		sentimentLabelDictionary.add("Neutral");
		sentimentLabelDictionary.add("Positive");
		sentimentLabelDictionary.add("Strongly Positive");

		Properties sentimentProps = new Properties();
		sentimentProps.put("annotators", "tokenize,ssplit,pos,parse,sentiment");
		sentimentPipeline = new StanfordCoreNLP(sentimentProps);

		Properties openIEProps = new Properties();
		openIEProps.put("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
		openIEPipeline = new StanfordCoreNLP(openIEProps);
	}

	public static Map<String, Object> getSentiment(String text) {
		Map<String, Object> sentimentAspects = new HashMap<>();

		Annotation annotation = sentimentPipeline.process(text);
		INDArray aggregateSentimentVector = Nd4j.ones(5);
		for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
			Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
			INDArray sentimentVector = Nd4j.create(RNNCoreAnnotations.getPredictionsAsStringList(tree));
			aggregateSentimentVector.muli(sentimentVector);
		}

		int sentimentIDX = aggregateSentimentVector.argMax(1).getInt(0);
		sentimentAspects.put("score", sentimentScoreDictionary.get(sentimentIDX));
		sentimentAspects.put("label", sentimentLabelDictionary.get(sentimentIDX));

		System.out.println(aggregateSentimentVector);
		System.out.println(sentimentAspects);

		return sentimentAspects;
	}

	public static List<Map<String, Object>> getSummaryAndSentiment(String text) {
		List<Map<String, Object>> summaries = new ArrayList<>();

		Annotation annotation = openIEPipeline.process(text);
		INDArray aggregateSentimentVector = Nd4j.ones(5);
		for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
			// Get the OpenIE triples for the sentence
			Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
			// Print the triples
			List<String> tripleTexts = new ArrayList<>();
			Map<String, Double> confidences = new HashMap<>();
			for (RelationTriple triple : triples) {
//				System.out.println(triple.confidence + "\t" + triple.subjectLemmaGloss() + "\t" + triple.relationLemmaGloss() + "\t" + triple.objectLemmaGloss());
				String tripleText = triple.subjectLemmaGloss() + " " + triple.relationLemmaGloss() + " " + triple.objectLemmaGloss();
				tripleTexts.add(tripleText);
				confidences.put(tripleText, triple.confidence);
			}

			if (tripleTexts.stream().max(Comparator.comparing(String::length)).isPresent()) {
				String summaryText = tripleTexts.stream().max(Comparator.comparing(String::length)).get();
				Map<String, Object> summary = new HashMap<>();
				summary.put("text", summaryText);
				summary.put("confidence", confidences.get(summaryText));
				summary.put("sentiment", getSentiment(summaryText));
				summaries.add(summary);
			}

		}

		return summaries;
	}
}

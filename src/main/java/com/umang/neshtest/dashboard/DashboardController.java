package com.umang.neshtest.dashboard;

import com.umang.neshtest.corenlp.NLPPipeline;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.*;

@Controller
public class DashboardController {
	@GetMapping("/")
	public String index(Model model) {
//		List<DashboardEntry> entries = StreamSupport
//				.stream(this.repository.findAll().spliterator(), true)
//				.map(p -> new DashboardEntry(p, githubClient.fetchEventsList(p.getOrgName(), p.getRepoName())))
//				.collect(Collectors.toList());
//		model.addAttribute("entries", entries);
		return "index";
	}

	@GetMapping("/{ticker}")
	public String dash(@PathVariable String ticker, Model model) throws IOException {
		String baseURL = "https://seekingalpha.com/symbol/" + ticker;

		String transcriptsURL = baseURL + "/earnings/transcripts";
		String stockPriceURL = "https://www.fool.com/quote/" + ticker;

		// Stock Price
		Document stockDoc = Jsoup.connect(stockPriceURL).get();
		model.addAttribute("ticker", ticker);
		model.addAttribute("company", stockDoc.selectFirst("span.company-name").text());
		Map<String, String> stockData = new HashMap<>();

		Element stockPriceElement = stockDoc.selectFirst("h2.current-price");
		if (stockPriceElement != null) {
			stockData.put("closing", stockPriceElement.text());
		}

		Element stockChangeAbsElement = stockDoc.selectFirst("h2.price-change-amount");
		if (stockChangeAbsElement != null) {
			stockData.put("changeAbs", stockChangeAbsElement.text());
		}

		Element stockChangePCElement = stockDoc.selectFirst("h2.price-change-percent");
		if (stockChangePCElement != null) {
			stockData.put("changePC", stockChangePCElement.text());
		}

		Element stockChangeElement = stockDoc.selectFirst("h2.price-change-arrow");
		if (stockChangeElement != null) {
			if (stockChangeElement.classNames().contains("price-neg")) {
				stockData.put("change", "down");
			}
			else {
				stockData.put("change", "up");
			}
		}


		Elements stockElements = stockDoc.select("table.key-data-points tr");
		for (Element stockElement : stockElements) {
			if (stockElement.children().first().text().contains("Prev Close")) {
				stockData.put("prevClose", stockElement.children().last().text());
			}
			if (stockElement.children().first().text().contains("Market Cap")) {
				stockData.put("marketCap", stockElement.children().last().text());
			}
			if (stockElement.children().first().text().trim().startsWith("Volume")) {
				stockData.put("volume", stockElement.children().last().text());
			}
			if (stockElement.children().first().text().contains("Open")) {
				stockData.put("open", stockElement.children().last().text());
			}
		}

		model.addAttribute("stock", stockData);

		Document doc = Jsoup.connect(baseURL).get();

		// News Articles
		List<Map<String, String>> newsArticles = new ArrayList<>();
		Element newsSection = doc.selectFirst("div.content_section.latest-news");
		Elements newsArticleSections = newsSection.select("ul#symbol-page-latest li");
		for (Element newsArticleItem : newsArticleSections) {
			String articleType = newsArticleItem.selectFirst("div.content div.date_on_by").child(0).text();
			if (articleType.equals("SA News")) {
				Map<String, String> newsArticle = new HashMap<>();
				newsArticle.put("date", newsArticleItem.selectFirst("div.content div.date_on_by").child(2).text());
				Element newsItem = newsArticleItem.selectFirst("div.content div.symbol_article a");
				newsArticle.put("headline", newsItem.text());
				newsArticle.put("url", newsItem.attributes().get("href"));
				newsArticles.add(newsArticle);
			}
		}
		model.addAttribute("news", newsArticles);

		Document transcriptListDoc = Jsoup.connect(transcriptsURL).get();
		Elements transcriptElements =transcriptListDoc.select("div.transcripts div.symbol_article");
		for (Element transcriptElement : transcriptElements) {
			if (transcriptElement.selectFirst("a").text().endsWith("Earnings Call Transcript")) {
				Map<String, String> transcript = new HashMap<>();
				transcript.put("headline", transcriptElement.selectFirst("a").text());
				transcript.put("url", transcriptElement.selectFirst("a").attributes().get("href"));
				model.addAttribute("transcript", transcript);
				break;
			}
		}

		return "dash";
	}

	@GetMapping("news/{newsID}")
	public String news(@PathVariable String newsID, Model model) throws IOException {
		Document articleDoc = Jsoup.connect("https://seekingalpha.com/news/" + newsID).get();
		String headline = articleDoc.selectFirst("h1[itemprop=\"headline\"]").text();
		Elements paragraphElements = articleDoc.select("p.bullets_li");
		List<Map<String, Object>> paragraphs = new ArrayList<>();
		for (Element p : paragraphElements) {
			if (p.text().trim().toLowerCase().startsWith("click to subscribe") || p.text().trim().toLowerCase().startsWith("now read")) {
				break;
			}
			Map<String, Object> paragraph = new HashMap<>();
			paragraph.put("text", p.text());
			paragraph.put("sentiment", NLPPipeline.getSentiment(p.text()));
			paragraphs.add(paragraph);
		}

		model.addAttribute("headline", headline);
		model.addAttribute("paragraphs", paragraphs);

		return "news";
	}

	@GetMapping("article/{articleID}")
	public String transcript(@PathVariable String articleID, Model model) throws IOException {
		Document articleDoc = Jsoup.connect("https://seekingalpha.com/article/" + articleID).get();
		String headline = articleDoc.selectFirst("h1[itemprop=\"headline\"]").text();
		model.addAttribute("headline", headline);

		Elements paragraphElements = articleDoc.select("div#a-body p");

		int idx = 0;
		Element temp = paragraphElements.first();
		model.addAttribute("description", temp.text());

		// Participants
		List<Map<String, Object>> participants = new ArrayList<>();
		while (idx < paragraphElements.size()) {
			temp = paragraphElements.get(idx++);
			if (temp.text().trim().equalsIgnoreCase("operator")) {
				System.out.println("Found Operator!");
				break;
			}
			if (temp.selectFirst("strong") != null) {
				Map<String, Object> part = new HashMap<>();
				part.put("title", temp.selectFirst("strong").text());
				List<String> people = new ArrayList<>();
				while (idx < paragraphElements.size()) {
					temp = paragraphElements.get(idx++);
					if (temp.selectFirst("strong") != null) {
						idx--;
						break;
					}
					people.add(temp.text());
				}
				part.put("people", people);
				participants.add(part);
			}
		}
		model.addAttribute("participants", participants);

		List<Map<String, Object>> summaries = new ArrayList<>();
		Map<String, Integer> speechLength = new HashMap<>();
		while (idx < paragraphElements.size()) {
			String speaker = temp.selectFirst("strong").text().trim();
			if (!speechLength.containsKey(speaker)) {
				speechLength.put(speaker, 0);
			}
			StringBuilder speech = new StringBuilder();
			List<String> people = new ArrayList<>();
			while (idx < paragraphElements.size()) {
				temp = paragraphElements.get(idx++);
				if (temp.selectFirst("strong") != null) {
					break;
				}
				speech.append(temp.text().trim()).append(" ");
			}
			speechLength.put(speaker, speech.length() + speechLength.get(speaker));
			if (!speaker.equalsIgnoreCase("operator")) {
				summaries.addAll(NLPPipeline.getSummaryAndSentiment(speech.toString()));
			}
		}
		model.addAttribute("summaries", summaries);
		String longestSpeaker = Collections.max(speechLength.entrySet(), Map.Entry.comparingByValue()).getKey();
		model.addAttribute("longestSpeaker", longestSpeaker);

		return "transcript";
	}
}

package com.bot.examples;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.io.FileWriter;
import java.io.IOException;

import dev.langchain4j.data.document.DocumentSplitter;


import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

import java.io.File;
import java.nio.file.Paths;

public class Scrape {
    public static void main(String[] args) {
        String disease = "Diabetes";
        String letter = disease.substring(0, 1);


        String diseaseUrl = findDiseaseUrl(letter, disease);

        if (diseaseUrl != null) {
            String diseaseContent = extractDiseaseContent(diseaseUrl);
            writeToFile(disease, diseaseContent);
        } else {
            System.out.println("Disease not found");
        }
    }

    private static String findDiseaseUrl(String letter, String disease) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet("https://www.mayoclinic.org/diseases-conditions/index?letter=" + letter);
            CloseableHttpResponse response = httpClient.execute(httpGet);

            Document doc = Jsoup.parse(response.getEntity().getContent(), "UTF-8", "https://www.mayoclinic.org/");

            Elements diseaseLinks = doc.select("div.cmp-azresults.cmp-azresults-from-model div.cmp-link a");

            for (Element link : diseaseLinks) {
                if (link.text().equalsIgnoreCase(disease)) {
                    return link.attr("abs:href");
                }
            }

        } catch (IOException e) {
            System.err.println("Error fetching disease list: " + e.getMessage());
        }
        return null;
    }

    private static String extractDiseaseContent(String diseaseUrl) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(diseaseUrl);
            CloseableHttpResponse response = httpClient.execute(httpGet);

            Document doc = Jsoup.parse(response.getEntity().getContent(), "UTF-8", diseaseUrl);
            StringBuilder contentBuilder = new StringBuilder();

            try {
                Elements contentElements = doc.select("div.content p, div.content ul, div.content h2, div.content h3, div.content h4");
                for (Element element : contentElements) {
                    contentBuilder.append(element.text()).append("\n");
                }
            } catch (Exception e) { // A slightly broader catch in case of different errors
                Elements contentElements = doc.select("div.container-child.container-child.cmp-column-control__content-column article section");
                for (Element element : contentElements) {
                    contentBuilder.append(element.text()).append("\n");
                }
            }

            return contentBuilder.toString();
        } catch (IOException e) {
            System.err.println("Error fetching disease page: " + e.getMessage());
            return null;
        }
    }

    private static void writeToFile(String disease, String diseaseContent) {
        try (FileWriter fileWriter = new FileWriter(  disease + ".txt")) {
            fileWriter.write(diseaseContent);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}


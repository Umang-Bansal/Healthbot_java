package com.bot.examples;

import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.document.Document;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import dev.langchain4j.model.embedding.EmbeddingModel;

public class MayoClinicQABot {
    private final Connection dbConnection;
    private final EmbeddingModel embeddingModel;
    private ConversationalRetrievalChain chain;
    private EmbeddingStoreIngestor ingestor;
    private final OpenAiChatModel chatModel;
    public static void main(String[] args) {
        JFrame frame = new JFrame("Mayo Clinic QABot");
        frame.setSize(400, 300); // Adjust the size as needed
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        Connection conn = getConnection();
        MayoClinicQABot bot = new MayoClinicQABot(conn);
        String answer = bot.answerQuestion("What are the symptoms of Asthma", "Asthma");
        System.out.println(answer);
    }
    public MayoClinicQABot(Connection dbConnection) {
        this.dbConnection = dbConnection;
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.chatModel = OpenAiChatModel.withApiKey(ApiKeys.OPENAI_API_KEY);
    }
    public String answerQuestion(String question, String disease) {
        String diseaseContent = getDiseaseDataFromDB(dbConnection, disease);
        if (diseaseContent == null) {
            scrapeDiseaseData(disease); // Assuming you have this function modified
            diseaseContent = getDiseaseDataFromDB(dbConnection, disease); // Retry
        }
        if (chain == null) {
            generateAnswer(question, diseaseContent); // Initialize chain and ingestor
            System.out.println("Chain is null! Did you call generateAnswer first?");
        } else {
            ingestor.ingest(List.of(new Document(diseaseContent)));
        }

        return chain.execute(question);
    }
    private String generateAnswer(String question, String diseaseContent) {
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300,0)) // Adjust split size
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        Document document = new Document(diseaseContent);
        ingestor.ingest(List.of(document));

        if (this.chain == null) {
            this.chain = ConversationalRetrievalChain.builder()
                    .chatLanguageModel(chatModel)
                    .retriever(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel))
                    .build();
        }
        System.out.println("generateAnswer method called");

        return chain.execute(question);
    }
    private String getDiseaseDataFromDB(Connection conn, String disease) {
        String query = "SELECT content FROM diseases WHERE disease_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, disease);
            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("content");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving data: " + e.getMessage());
        }
        return null;
    }

    public void scrapeDiseaseData(String disease) {
        String diseaseUrl = findDiseaseUrl(disease.substring(0, 1), disease);
        if (diseaseUrl != null) {
            String diseaseContent = extractDiseaseContent(diseaseUrl);
            storeInDatabase(disease, diseaseContent);
        } else {
            System.out.println("Disease not found");
        }
    }


    private static String findDiseaseUrl(String letter, String disease) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet("https://www.mayoclinic.org/diseases-conditions/index?letter=" + letter);
            CloseableHttpResponse response = httpClient.execute(httpGet);

            org.jsoup.nodes.Document doc = Jsoup.parse(response.getEntity().getContent(), "UTF-8", "https://www.mayoclinic.org/");

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

    public static Connection getConnection() {
        try {
            String dbURL = "jdbc:mysql://localhost:3306/umang"; // Adjust for your database
            String username = "root";
            String password = "1234";
            return DriverManager.getConnection(dbURL, username, password);
        } catch (SQLException e) {
            System.err.println("Error establishing database connection: " + e.getMessage());
            return null;
        }
    }

    private static String extractDiseaseContent(String diseaseUrl) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(diseaseUrl);
            CloseableHttpResponse response = httpClient.execute(httpGet);

            org.jsoup.nodes.Document doc = Jsoup.parse(response.getEntity().getContent(), "UTF-8", diseaseUrl);

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
    private void storeInDatabase(String disease, String diseaseContent) {
        try (PreparedStatement stmt = dbConnection.prepareStatement(
                "INSERT INTO diseases (disease_name, content) VALUES (?, ?)")) {
            stmt.setString(1, disease);
            stmt.setString(2, diseaseContent);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error storing disease data: " + e.getMessage());
        }
    }
}


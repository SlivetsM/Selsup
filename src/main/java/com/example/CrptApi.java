package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final int requestLimit;
    private final ScheduledExecutorService scheduler;
    private final Semaphore semaphore;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();

        long period = timeUnit.toMillis(1);
        this.scheduler.scheduleAtFixedRate(semaphore::release, period, period, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Object document, String signature) throws IOException, InterruptedException {
        semaphore.acquire();

        HttpPost post = new HttpPost(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"));
        post.setHeader("Content-Type", "application/json");
        post.setHeader("Signature", signature);

        // Convert document to JSON string
        String json = objectMapper.writeValueAsString(document);
        post.setEntity(new StringEntity(json));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode + " Response: " + responseBody);
            }
            System.out.println("Response: " + responseBody);
        } finally {
            semaphore.release();
        }
    }

    public void shutdown() throws IOException {
        scheduler.shutdown();
        httpClient.close();
    }
}

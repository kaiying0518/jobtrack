package com.example.jobtrack;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobtrackApplication {

    private static final String APP_URL = "http://localhost:8080";

    public static void main(String[] args) {
        if (isAlreadyRunning()) {
            openBrowser(APP_URL);
            return;
        }

        openBrowserLater();
        SpringApplication.run(JobtrackApplication.class, args);
    }

    private static boolean isAlreadyRunning() {
        try {
            URL url = URI.create(APP_URL).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(800);
            connection.setReadTimeout(800);

            int statusCode = connection.getResponseCode();
            return statusCode >= 200 && statusCode < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static void openBrowserLater() {
        new Thread(() -> {
            for (int i = 0; i < 30; i++) {
                try {
                    Thread.sleep(1000);

                    if (isAlreadyRunning()) {
                        openBrowser(APP_URL);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
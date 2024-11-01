package org.keycloak.authentication.authenticators.browser;

import io.quarkus.security.Authenticated;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Authenticated
public class CustomRequest {

    private static String postUrl;
    public CustomRequest(String url){
        this.postUrl = url;
        System.out.println("Custom request sınıfı gelen atanan değer: "+ postUrl);

    }

    private static String fetchUrlFromDb(){
        String targetUrl = "http://localhost:8080/realms/master/login-actions/getMobilsignData";

        try {

            // Create a URL object from the API URL
            URL url = new URL(targetUrl);

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to GET
            connection.setRequestMethod("GET");

            // Get the response code
            int responseCode = connection.getResponseCode();

            // If the response code indicates success (200)
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response data
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();

                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                System.out.println("fetch functtt..."+response);

                return response.toString();
            } else {
                // Handle error cases here, e.g., log the response code
                System.out.println("Error: " + responseCode);
            }
        } catch (Exception e) {
            // Handle exceptions, e.g., connection error
            e.printStackTrace();
        }

        // Return the response data as a string
        return "";
    }


    public static boolean sendHttpPOSTRequest(String postParamKonu, String postParamOperator, String postParamTel, String postParamTC) throws IOException {

        String requestBody = String.format("{\"konu\":\"%s\",\"operator\":\"%s\",\"telNo\":\"%s\",\"tcKimlikNo\":\"%s\"}",
                postParamKonu, postParamOperator, postParamTel, postParamTC);

        boolean success = false;

        try {
            String belgenetUrl = fetchUrlFromDb();
            System.out.println(belgenetUrl);

            URL url = new URL(belgenetUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                    String responseLine;
                    StringBuilder response = new StringBuilder();
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    System.out.println("Response: " + response.toString());
                    if (response.substring(12, 16).equals("true")) {
                        success = true;
                    }

                }

            } else {
                System.out.println("HTTP request failed with response code: " + responseCode);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return success;
    }
}

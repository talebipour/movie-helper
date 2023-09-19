package com.github.talebipour.moviehelper.util.opensubtitles;

import com.github.talebipour.moviehelper.model.Subtitle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class OpenSubtitlesClient {

    private static final String API_URL = "https://api.opensubtitles.com/api/v1/subtitles";
    private RestTemplate restTemplate;
    private String apiKey;

    @Autowired
    public OpenSubtitlesClient(RestTemplateBuilder restTemplateBuilder, @Value("${opensubtitles.api.key}") String apiKey) {
        this.restTemplate = restTemplateBuilder.build();
        this.apiKey = apiKey;
    }

    public List<Subtitle> searchByFileHash(String hash, String language) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        headers.set("User-Agent", "");
        var requestEntity = new HttpEntity<>(headers);
        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("moviehash", hash)
                .queryParam("language", language)
                .encode()
                .toUriString();
        ResponseEntity<SearchResponse> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity,
                SearchResponse.class);
        if (response.getStatusCode().isError()) {
            throw new AssertionError("API invocation failed, status:" + response.getStatusCode());
        }
        return response.getBody().toSubtitles();
    }
}

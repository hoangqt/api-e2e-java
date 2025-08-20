package com.github.hoangqt;

import java.io.FileInputStream;
import java.util.Properties;

public class TestProperties {
    private Properties properties;
    private String token;
    private String owner;

    private static final String GITHUB_PAT_KEY = "github-pat";
    private static final String REPO_OWNER_KEY = "owner";

    private static final String TEST_PROPERTIES_FILE = "src/test/resources/test.properties";

    private TestProperties() {
    }

    public static TestProperties create() {
        var properties = new TestProperties();
        properties.loadProperties();
        properties.parseToken();
        properties.parseOwner();
        return properties;
    }

    private void loadProperties() {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream(TEST_PROPERTIES_FILE)) {
            properties.load(fis);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test.properties", e);
        }
    }

    private void parseToken() {
        if (properties.getProperty(GITHUB_PAT_KEY) != null) {
            token = properties.getProperty(GITHUB_PAT_KEY);
        } else {
            String githubToken = System.getenv("GITHUB_PAT");
            if (githubToken != null && !githubToken.trim().isEmpty()) {
                token = githubToken;
            }
        }
    }

    private void parseOwner() {
        if (properties.getProperty(REPO_OWNER_KEY) != null) {
            owner = properties.getProperty(REPO_OWNER_KEY);
        }
    }

    public String getToken() {
        return token;
    }

    public String getOwner() {
        return owner;
    }
}

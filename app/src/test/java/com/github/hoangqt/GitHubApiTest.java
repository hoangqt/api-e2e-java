package com.github.hoangqt;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GitHubApiTest {
    private static Properties properties;
    private static String token;
    private static GitHub github;

    private static final String GITHUB_PAT_KEY = "github-pat";
    private static final String TEST_REPO = "ansible";
    private static final String TEST_OWNER = "hoangqt";

    private static String issueNumber;

    @BeforeAll
    public static void setup() throws IOException {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream("src/test/resources/test.properties")) {
            properties.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (properties.getProperty(GITHUB_PAT_KEY) != null) {
            token = properties.getProperty(GITHUB_PAT_KEY);
        } else {
            String githubToken = System.getenv("GITHUB_PAT");
            if (githubToken != null || !githubToken.trim().isEmpty()) {
                token = githubToken;
            }
        }
        github = new GitHub(token, properties.getProperty("owner"));
    }

    @Test
    public void testGetRepository() {
        var json = github.getRepository(TEST_REPO)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();
        assertThat(json.getString("name")).isEqualTo(TEST_REPO);
        assertThat(json.getString("owner.login")).isEqualTo(TEST_OWNER);
    }

    @Test
    @Order(1)
    public void testCreateIssue() {
        var body = """
        {
            "title": "Found a bug",
            "body": "This is a test issue created by automation",
            "assignees": ["hoangqt"],
            "labels": ["bug"]
        }""";

        var json = github.createIssue(TEST_REPO, body)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(json.getString("title")).isEqualTo("Found a bug");
        assertThat(json.getString("body")).isEqualTo("This is a test issue created by automation");
        assertThat(json.getString("state")).isEqualTo("open");

        issueNumber = String.valueOf(json.getInt("number"));
    }

    @Test
    @Order(2)
    public void testGetIssues() throws InterruptedException {
        // Poll for the issue to appear due to eventual consistency
        long timeoutMillis = 15_000L;
        long pollIntervalMillis = 500L;
        long deadline = System.currentTimeMillis() + timeoutMillis;

        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            var json = github.getRepositoryIssues(TEST_REPO)
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            var titles = json.getList("title", String.class);
            if (titles != null && titles.contains("Found a bug")) {
                found = true;

                // If issueNumber wasn't set in testCreateIssue (e.g., rerun), set it now
                if (issueNumber == null) {
                    List<Map<String, Object>> issues = json.getList("$");
                    for (Map<String, Object> issue : issues) {
                        if ("Found a bug".equals(issue.get("title"))) {
                            Object number = issue.get("number");
                            if (number != null) {
                                issueNumber = String.valueOf(number);
                            }
                            break;
                        }
                    }
                }
                break;
            }
            Thread.sleep(pollIntervalMillis);
        }

        assertThat(found).as("Issue titled 'Found a bug' should appear within the polling timeout").isTrue();
        assertThat(issueNumber).as("Issue number for title 'Found a bug' should be found").isNotNull();
    }

    @Test
    @Order(3)
    public void testUpdateIssue() {
        var body = """
        {
            "title": "Found a bug",
            "body": "This is a test issue created by automation",
            "assignees": ["hoangqt"],
            "labels": ["bug", "invalid"]
        }""";

        var json = github.updateIssue(TEST_REPO, body, issueNumber)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(json.getString("labels")).containsAnyOf("bug", "invalid");
    }

    @Test
    public void testGetCommits() {
        var json = github.getRepositoryCommits(TEST_REPO)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();
        assertThat(json.getList("committer.login", String.class)).contains(TEST_OWNER);
    }
}
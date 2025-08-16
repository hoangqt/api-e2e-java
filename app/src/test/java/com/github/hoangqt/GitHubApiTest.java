package com.github.hoangqt;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubApiTest {
    private static Properties properties;
    private static String token;
    private static GitHub github;

    @BeforeAll
    public static void setup() throws IOException {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream("src/test/resources/test.properties")) {
            properties.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (properties.getProperty("github-pat") != null) {
            token = properties.getProperty("github-pat");
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
        var json = github.getRepository("ansible")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();
        assertThat(json.getString("name")).isEqualTo("ansible");
        assertThat(json.getString("owner.login")).isEqualTo("hoangqt");
    }


    @Test
    public void testGetIssues() {
        var json = github.getRepositoryIssues("ansible")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();
        assertThat(json.getList("title", String.class)).contains("Test");
        assertThat(json.getList("number", Integer.class)).contains(5);
    }

    @Test
    public void testGetCommits() {
        var json = github.getRepositoryCommits("ansible")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();
        assertThat(json.getList("committer.login", String.class)).contains("hoangqt");
    }

    @Test
    public void testCreateIssue() {
        var body = """
        {
            "title": "Found a bug",
            "body": "This is a test issue created by automation",
            "assignees": ["hoangqt"],
            "labels": ["bug"]
        }""";

        var json = github.createIssue("ansible", body)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(json.getString("title")).isEqualTo("Found a bug");
        assertThat(json.getString("body")).isEqualTo("This is a test issue created by automation");
        assertThat(json.getString("state")).isEqualTo("open");
    }

    @Test
    public void testUpdateIssue() {
        var body = """
        {
            "title": "Found a bug",
            "body": "This is a test issue created by automation",
            "assignees": ["hoangqt"],
            "labels": ["bug", "invalid"]
        }""";

        var issueNumber = "8";

        var json = github.updateIssue("ansible", body, issueNumber)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        assertThat(json.getString("labels")).containsAnyOf("bug", "invalid");
    }
}
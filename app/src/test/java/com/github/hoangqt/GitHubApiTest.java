package com.github.hoangqt;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GitHubApiTest {
  private static GitHub github;

  private static final String TEST_REPO = "sandbox";
  private static final String TEST_OWNER = "hoangqt";

  private static String issueNumber;

  @BeforeAll
  public static void setup() throws RuntimeException {
    var testProperties = TestProperties.create();
    github = new GitHub(testProperties.getToken(), testProperties.getOwner());
  }

  @Test
  public void testGetRepository() {
    var json =
        github
            .getRepository(TEST_REPO)
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
    var body =
        """
        {
            "title": "Found a bug",
            "body": "This is a test issue created by automation",
            "assignees": ["hoangqt"],
            "labels": ["bug"]
        }""";

    var json =
        github
            .createIssue(TEST_REPO, body)
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
      var json =
          github
              .getRepositoryIssues(TEST_REPO)
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

    assertThat(found)
        .as("Issue titled 'Found a bug' should appear within the polling timeout")
        .isTrue();
    assertThat(issueNumber).as("Issue number for title 'Found a bug' should be found").isNotNull();
  }

  @Test
  @Order(3)
  public void testUpdateIssue() {
    var body =
        """
        {
            "title": "Found a bug",
            "body": "This is a test issue created by automation",
            "assignees": ["hoangqt"],
            "labels": ["bug", "invalid"]
        }""";

    var json =
        github
            .updateIssue(TEST_REPO, body, issueNumber)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .jsonPath();

    assertThat(json.getString("labels")).containsAnyOf("bug", "invalid");
  }

  /** Example of a timeout configuration workaround to missing request cancellation feature. */
  @Test
  public void testSlowRequest() {
    var json = github.getSlowRequest(TEST_REPO).then().statusCode(200).extract().jsonPath();
    assertThat(json.getString("name")).isEqualTo(TEST_REPO);
    assertThat(json.getString("owner.login")).isEqualTo(TEST_OWNER);
  }

  @Test
  public void testGetCommits() {
    var json =
        github
            .getRepositoryCommits(TEST_REPO)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .jsonPath();
    assertThat(json.getList("committer.login", String.class)).contains(TEST_OWNER);
  }

  @AfterAll
  public static void tearDown() {
    int page = 1;
    while (true) {
      var json =
          github
              .getRepositoryIssues(TEST_REPO, page)
              .then()
              .statusCode(200)
              .contentType(ContentType.JSON)
              .extract()
              .jsonPath();

      List<Map<String, Object>> issues = json.getList("$");
      if (issues == null || issues.isEmpty()) {
        break;
      }

      var body =
          """
              {
                  "state": "closed",
                  "state_reason": "not_planned"
              }""";

      for (Map<String, Object> issue : issues) {
        Object number = issue.get("number");
        if (number != null) {
          var issueNum = String.valueOf(number);
          github.updateIssue(TEST_REPO, body, issueNum).then().statusCode(200);
        }
      }
      page++;
    }
  }
}

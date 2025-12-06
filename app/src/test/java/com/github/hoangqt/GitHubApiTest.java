package com.github.hoangqt;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
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
  private static final String ISSUE_TITLE = "Found a bug";
  private static final String ISSUE_BODY = "This is a test issue created by automation";
  private static final String CLOSED = "closed";
  private static final String NOT_PLANNED = "not_planned";

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
    var assignees = new JsonArray();
    assignees.add(TEST_OWNER);

    var labels = new JsonArray();
    labels.add("bug");

    var body = new JsonObject();
    body.addProperty("title", ISSUE_TITLE);
    body.addProperty("body", ISSUE_BODY);
    body.add("assignees", assignees);
    body.add("labels", labels);

    var json =
        github
            .createIssue(TEST_REPO, body.toString())
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .jsonPath();

    assertThat(json.getString("title")).isEqualTo(ISSUE_TITLE);
    assertThat(json.getString("body")).isEqualTo(ISSUE_BODY);
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
      if (titles != null && titles.contains(ISSUE_TITLE)) {
        found = true;

        // If issueNumber wasn't set in testCreateIssue (e.g., rerun), set it now
        if (issueNumber == null) {
          List<Map<String, Object>> issues = json.getList("$");
          for (Map<String, Object> issue : issues) {
            if (ISSUE_TITLE.equals(issue.get("title"))) {
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
        .as("Issue titled " + "'Found a bug'" + " should appear within the polling timeout")
        .isTrue();
    assertThat(issueNumber)
        .as("Issue number for title " + "'Found a bug'" + " should be found")
        .isNotNull();
  }

  @Test
  @Order(3)
  public void testUpdateIssue() {
    var assignees = new JsonArray();
    assignees.add(TEST_OWNER);

    var labels = new JsonArray();
    labels.add("bug");
    labels.add("invalid");

    var body = new JsonObject();
    body.addProperty("title", ISSUE_TITLE);
    body.addProperty("body", ISSUE_BODY);
    body.add("assignees", assignees);
    body.add("labels", labels);

    // base 1s, multiplier 2.0, randomization 0.5
    IntervalFunction exponentialBackoff =
        IntervalFunction.ofExponentialRandomBackoff(1000, 2.0, 0.5);

    RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(5)
            .intervalFunction(exponentialBackoff)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(IOException.class, TimeoutException.class)
            .build();

    Retry retry = Retry.of("testUpdateIssue", config);

    var json =
        retry.executeSupplier(
            () ->
                github
                    .updateIssue(TEST_REPO, body.toString(), issueNumber)
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath());

    List<String> labelNames = json.getList("labels.name", String.class);
    assertThat(labelNames).containsAnyOf("bug", "invalid");
  }

  // Example of a timeout configuration workaround to missing request cancellation feature.
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

      var body = new JsonObject();
      body.addProperty("state", CLOSED);
      body.addProperty("state_reason", NOT_PLANNED);

      for (Map<String, Object> issue : issues) {
        Object number = issue.get("number");
        if (number != null) {
          var issueNum = String.valueOf(number);
          github.updateIssue(TEST_REPO, body.toString(), issueNum).then().statusCode(200);
        }
      }
      page++;
    }
  }
}

package com.github.hoangqt;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHub {
  private static final String GITHUB_API_URL = "https://api.github.com";
  private static final Logger logger = LoggerFactory.getLogger(GitHub.class);

  private String token;
  private String owner;

  static {
    RestAssured.baseURI = GITHUB_API_URL;
  }

  public GitHub(String token, String owner) {
    this.token = token;
    this.owner = owner;
  }

  public String getOwner() {
    return owner;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public Response getRepository(String repo) {
    String url = String.format("/repos/%s/%s", this.owner, repo);
    logger.debug("Fetching repository from {}", url);
    return given()
        .header("Authorization", "Bearer " + this.token)
        .header("Accept", "application/vnd.github.v3+json")
        .pathParam("owner", this.owner)
        .pathParam("repo", repo)
        .when()
        .get("/repos/{owner}/{repo}");
  }

  public Response getRepositoryIssues(String repo) {
    return getRepositoryIssues(repo, 1);
  }

  public Response getRepositoryIssues(String repo, int page) {
    int defaultPerPage = 30;
    String url = String.format("/repos/%s/%s/issues", this.owner, repo);
    logger.debug("Fetching issues from {}", url);

    return given()
        .header("Authorization", "Bearer " + this.token)
        .header("Accept", "application/vnd.github.v3+json")
        .pathParam("owner", this.owner)
        .pathParam("repo", repo)
        .queryParam("per_page", defaultPerPage)
        .queryParam("page", page)
        .when()
        .get("/repos/{owner}/{repo}/issues");
  }

  public Response getRepositoryCommits(String repo) {
    String url = String.format("/repos/%s/%s/commits", this.owner, repo);
    logger.debug("Fetching commits from {}", url);
    return given()
        .header("Authorization", "Bearer " + this.token)
        .header("Accept", "application/vnd.github.v3+json")
        .pathParam("owner", this.owner)
        .pathParam("repo", repo)
        .when()
        .get("/repos/{owner}/{repo}/commits");
  }

  public Response createIssue(String repo, String body) {
    String url = String.format("/repos/%s/%s/issues", this.owner, repo);
    logger.debug("Creating issue in {}", url);
    return given()
        .header("Authorization", "Bearer " + this.token)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .pathParam("owner", this.owner)
        .pathParam("repo", repo)
        .body(body)
        .when()
        .post("/repos/{owner}/{repo}/issues");
  }

  public Response updateIssue(String repo, String body, String issueNumber) {
    String url = String.format("/repos/%s/%s/issues/%s", this.owner, repo, issueNumber);
    logger.debug("Updating issue at {}", url);
    return given()
        .header("Authorization", "Bearer " + this.token)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .pathParam("owner", this.owner)
        .pathParam("repo", repo)
        .pathParam("issueNumber", issueNumber)
        .body(body)
        .when()
        .patch("/repos/{owner}/{repo}/issues/{issueNumber}");
  }

  /**
   * Sends a GET request to retrieve repositories from a GitHub repository with a customized timeout
   * configuration.
   *
   * @param repo the name of the repository from which to fetch issues
   * @return the response containing the issues or an error if the request fails
   */
  public Response getSlowRequest(String repo) {
    String url = String.format("/repos/%s/%s", this.owner, repo);
    logger.debug("Fetching repository (slow request) from {}", url);
    return given()
        .config(
            RestAssuredConfig.config()
                .httpClient(
                    HttpClientConfig.httpClientConfig()
                        // Apache HttpClient settings in milliseconds
                        .setParam("http.connection.timeout", 5000)
                        .setParam("http.socket.timeout", 5000)))
        .pathParam("owner", this.owner)
        .pathParam("repo", repo)
        .when()
        .get("/repos/{owner}/{repo}");
  }
}

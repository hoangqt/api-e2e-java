package com.github.hoangqt;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

public class GitHub {
    private static final String GITHUB_API_URL = "https://api.github.com";

    private final String token;
    private final String owner;

    public GitHub(String token, String owner) {
        this.token = token;
        this.owner = owner;
        RestAssured.baseURI = GITHUB_API_URL;
    }

    public Response getRepository(String repo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .pathParam("owner", this.owner)
                .pathParam("repo", repo)
                .when()
                .get("/repos/{owner}/{repo}");
    }

    public Response getRepositoryIssues(String repo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .pathParam("owner", this.owner)
                .pathParam("repo", repo)
                .when()
                .get("/repos/{owner}/{repo}/issues");
    }

    public Response getRepositoryCommits(String repo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .pathParam("owner", this.owner)
                .pathParam("repo", repo)
                .when()
                .get("/repos/{owner}/{repo}/commits");
    }

    public Response createIssue(String repo, String body) {
        return given()
                .header("Authorization", "Bearer " + token)
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
        return given()
                .header("Authorization", "Bearer " + token)
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
}

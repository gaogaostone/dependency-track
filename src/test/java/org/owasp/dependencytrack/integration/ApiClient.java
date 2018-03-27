/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.owasp.dependencytrack.integration;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.FileUtils;
import org.datanucleus.util.Base64;
import org.json.JSONObject;
import org.owasp.dependencytrack.util.HttpClientFactory;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ApiClient {

    static {
        Unirest.setHttpClient(HttpClientFactory.createClient());
    }

    private String baseUrl;
    private String apiKey;

    public ApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public UUID createProject(String name, String version) throws UnirestException {
        final HttpResponse<JsonNode> response = Unirest.put(baseUrl + "/api/v1/project")
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .body(new JSONObject()
                        .put("name", name)
                        .put("version", version)
                )
                .asJson();
        if (response.getStatus() == 201) {
            return UUID.fromString(response.getBody().getObject().getString("uuid"));
        }
        System.out.println("Error creating project " + name + " status: " + response.getStatus());
        return null;
    }

    public boolean uploadBom(UUID uuid, File bom) throws IOException, UnirestException {
        final HttpResponse<JsonNode> response = Unirest.put(baseUrl + "/api/v1/bom")
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .body(new JSONObject()
                        .put("project", uuid.toString())
                        .put("bom", Base64.encode(FileUtils.readFileToByteArray(bom)))
                )
                .asJson();
        return (response.getStatus() == 200);
    }

    public boolean uploadScan(UUID uuid, File scan) throws IOException, UnirestException {
        final HttpResponse<JsonNode> response = Unirest.put(baseUrl + "/api/v1/scan")
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .body(new JSONObject()
                        .put("project", uuid.toString())
                        .put("scan", Base64.encode(FileUtils.readFileToByteArray(scan)))
                )
                .asJson();
        return (response.getStatus() == 200);
    }

}

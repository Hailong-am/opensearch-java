/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

plugins {
    java
    `java-library`
    `maven-publish`
    id("opensearch-java.spotless-conventions")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // Upstream OSS client -- same jar, same package names
    api(project(":java-client"))

    // AWS SDK v2 -- required for SigV4 transport and credential chain
    implementation("software.amazon.awssdk", "auth", "[2.21,3.0)")
    implementation("software.amazon.awssdk", "sdk-core", "[2.21,3.0)")
    implementation("software.amazon.awssdk", "http-auth-aws", "[2.21,3.0)")
    // Default HTTP client (better performance than Apache)
    implementation("software.amazon.awssdk", "aws-crt-client", "[2.21,3.0)")

    testImplementation("junit", "junit", "4.13.2")
}

configurations {
    all {
        exclude(group = "software.amazon.awssdk", module = "third-party-jackson-core")
    }
}

tasks.test {
    // JUnit 4 (matches rest of project)
}

# app-lambda

The application as an AWS Lambda function: the `assembly` behind the Amazon Lambda REST
extension, which turns API Gateway REST API (v1) proxy events into ordinary requests for the HTTP
adapter (ADR 0006). SnapStart is enabled so the JVM is restored from a snapshot instead of started.

`./gradlew :app-lambda:build` produces `build/function.zip`. The SAM function uses it with the
handler `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest` on the `java25`
runtime. Configuration that differs per environment, the OIDC issuer, the backend client and the
data source, is supplied by the deployment as environment variables. The gateway has no
authoriser, so every path, `/q/health` and `/q/openapi` included, needs a token issued to the
backend client.

On AWS the data source logs in to RDS Proxy with an IAM authentication token from the
Quarkiverse Amazon RDS credentials provider, so this module, unlike `app-server`, depends on the
AWS SDK. Locally and in tests it uses the Dev Services password.

The test sends requests through the mock event server the extension provides in tests, so the
event conversion is exercised, against a PostgreSQL started by Dev Services.

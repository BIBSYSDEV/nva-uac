# infrastructure

The AWS deployment, as two stacks per account.

- `data-tier.yaml`, in this directory, is deployed once per account and rarely changes: the
  security groups, the Aurora PostgreSQL Serverless v2 cluster with a writer and a reader, and
  RDS Proxy with a read-only endpoint. Storage and the master secret are encrypted with the
  AWS-managed keys. It publishes what the application needs under `/NVA/UAC/...` in SSM,
  following the house convention. The VPC and subnets are inputs (D14).

  Fixed at the first deployment, because a change replaces the cluster and starts from an empty
  database: `DatabaseName`, `MasterUsername`, the cluster identifier derived from `Environment`,
  and the subnet group. `UpdateReplacePolicy: Snapshot` keeps a snapshot of the old cluster if
  that happens by mistake, but the data has to be restored by hand.

  The proxy requires IAM authentication. Every caller, the application and the migration step
  alike, connects with a token from `rds generate-db-auth-token`, never with the master password.
  The data tier publishes a managed policy granting `rds-db:connect` on the proxy under
  `/NVA/UAC/Database/MigrationConnectPolicyArn`, which the migration project's role attaches.
- `template.yaml`, at the repository root where the pipeline and the shared workflow expect it,
  is the SAM application stack: the Lambda function, the HTTP API with its JWT authoriser, the
  API mapping on the shared domain, the alarms, and the CodeBuild projects that migrate and
  rehearse inside the VPC. The error-rate alarm counts the 5xx responses clients receive from
  the API, since the Quarkus adapter turns an exception into a 500 response that Lambda counts
  as a successful invocation.

The edge in front of the shared domain, CloudFront and WAF, is not part of either stack: it is
the platform's shared edge, where every NVA API sits behind the same distribution and rules.
Whether this API needs anything of its own there is a Phase 1 decision, to be taken with the
platform team.

`./gradlew lintInfrastructure` runs `sam validate --lint` on both, as the pipeline does; it
needs the SAM CLI, which the shared GitHub workflow does not have, so it is not part of
`check`. The shared workflow lints `template.yaml` with cfn-lint itself.

Known follow-ups before Phase 1: the application connects through the proxy as the master user,
which must become a least-privilege database user with its own secret; and the proxy's IAM
authentication needs the Quarkiverse Amazon RDS credentials provider on the application side.

## Pipeline

Delivery follows the other NVA services (ADR 0005). The pipeline itself is created by
`NVA-infrastructure`, once per account, from `pipelines_master_template.yml`; registering this
repository there is the step outside this repository. Its stages are fixed:

1. **Source** pulls `main`.
2. **Build** runs `buildspec.yaml` in a CodeBuild project with Docker, because the tests start
   PostgreSQL: the Gradle build with every gate, the template lints, `sam package`, and an upload
   of the sources to the artefact bucket for the projects below. The artefact is `packaged.yaml`,
   with the bucket and the object version of the sources substituted in.
3. **Deploy** updates the stack with CloudFormation, after a manual approval where the account
   asks for one. The `Migration` custom resource starts the migration project with the uploaded
   sources, and the function waits for it, so the schema is current before a new version takes
   traffic. Deleting a branch stack drops its schema the same way; the `public` schema is never
   dropped.
4. **CopyDocs** copies OpenAPI documents from `docs/`; this repository keeps its contract in
   `adapter-http` for now.

The projects the stack defines, both inside the data tier's VPC and connecting as the master
user with an IAM token through the proxy:

- `<stack>-migration` runs `buildspec.migrate.yml`: `flywayMigrate`, or `flywayClean` of a
  branch schema when `DROP_SCHEMA` is `true`. The build answers CloudFormation itself when the
  custom resource started it.
- `<stack>-rehearsal` runs `buildspec.rehearse.yml` on demand, after the extract has filled the
  staging schema, and keeps `rehearsal-units.md` as its artefact under `nva-uac/rehearsals/` in
  the artefact bucket:

  ```
  aws codebuild start-build --project-name <stack>-rehearsal
  ```

`.github/workflows/build.yml` runs the shared BIBSYSDEV Java workflow on pull requests and
`main`, with Java 25; the OpenAPI lint is left to the Gradle build, since the contract does not
live in `docs/`.

## Branch stacks

A change that needs to be seen running before review is deployed by hand to the dev account,
against the shared data tier, with a schema of its own. Upload the sources as the Build stage
does, then deploy with the schema named after the branch, prefixed so it can never be `public`:

```
zip --quiet --recurse-paths migration-source.zip . --exclude '.git/*' 'build/*' '*/build/*' '.gradle/*' '*/.gradle/*'
VERSION=$(aws s3api put-object --bucket "$BUCKET" --key nva-uac/migration-source.zip --body migration-source.zip --query VersionId --output text)
./gradlew :app-lambda:build -x test
sam deploy --stack-name nva-uac-branch-<slug> --s3-bucket "$BUCKET" --capabilities CAPABILITY_IAM \
  --parameter-overrides CustomDomainBasePath=uac-<slug> SchemaName=branch_<slug> \
  MigrationSourceBucket="$BUCKET" MigrationSourceVersion="$VERSION"
sam delete --stack-name nva-uac-branch-<slug>
```

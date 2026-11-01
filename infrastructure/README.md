# infrastructure

The AWS deployment, as two stacks per account.

- `data-tier.yaml` is deployed once per account and rarely changes: the KMS key, the security
  groups, the Aurora PostgreSQL Serverless v2 cluster with a writer and a reader, and RDS Proxy
  with a read-only endpoint. It publishes what the application needs under `/NVA/UAC/...` in SSM,
  following the house convention. The VPC and subnets are inputs (D14).
- `template.yaml` is the SAM application stack: the Lambda function, the HTTP API with its JWT
  authoriser, the API mapping on the shared domain, and the alarms. Branch stacks deploy this
  one against the shared data tier of the dev account, each with its own schema.

`./gradlew lintInfrastructure` runs `sam validate --lint` on both, as the pipeline does;
it needs the SAM CLI, which the shared GitHub workflow does not have, so it is not part of
`check`. Deploying needs an account; see the pipeline for the commands.

Known follow-ups before Phase 1: the application connects through the proxy as the master user,
which must become a least-privilege database user with its own secret; and the proxy's IAM
authentication needs the Quarkiverse Amazon RDS credentials provider on the application side.

## Pipeline

- `buildspec.yml` builds and tests everything, lints the templates and packages the SAM
  application; the artefact is `packaged.yaml`. The CodeBuild project needs privileged mode,
  because the tests start PostgreSQL in Docker.
- `buildspec.migrate.yml` applies the migrations, or drops a branch schema, from a CodeBuild
  project that runs inside the data tier's VPC. The pipeline runs it before the function alias
  is shifted. `scripts/run-codebuild.sh` starts such a project and waits for it.
- `.github/workflows/build.yml` runs the shared BIBSYSDEV Java workflow on pull requests and
  `main`, with Java 25.
- `.github/workflows/branch-stack.yml` deploys every branch as its own stack in the dev account
  with a schema of its own on the shared cluster, smoke-tests `/q/health`, and removes both. The
  deploy role, region, artefact bucket and migration project are repository variables.

The pipeline itself, CodePipeline with these projects, is defined with the other NVA pipelines
outside this repository.

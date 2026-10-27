# ADR 0005: Delivery on the NVA CodePipeline

Date: 2026-10-27

Status: Accepted

## Context

The first pipeline deployed every branch as its own stack from a GitHub Actions workflow, with
a migration step started through a CodeBuild project defined elsewhere. Review found that this
is not how the NVA services are delivered. Every service is deployed by a CodePipeline that
`NVA-infrastructure` creates per account from `pipelines_master_template.yml`: Source from
`main`, Build with the repository's `buildspec.yaml`, Deploy of the resulting `packaged.yaml`
with CloudFormation, with a manual approval where the account asks for one, and a copy of the
docs. GitHub Actions run the shared BIBSYSDEV Java workflow and nothing else. Review also found
that the migration step connected with the master password while the proxy requires IAM
authentication, that the build artefact lacked what the migration needed, and that the branch
workflow could run twice at once against the same schema.

## Decision

nva-uac is delivered like the other NVA services. The SAM application is `template.yaml` at the
root, the Build stage runs `buildspec.yaml`, and `NVA-infrastructure` owns the pipeline. The
GitHub workflow calls the shared Java workflow, pinned to a commit.

The stages are fixed, so the migration is part of the stack: a CodeBuild project inside the
VPC, started by a custom resource on every deploy with the sources that build uploaded, which
the function depends on so the schema is current before a new version takes traffic. The
project connects as the master user with an IAM token through the proxy, under the policy the
data tier publishes. A second project rehearses the Cristin migration on demand and keeps the
report. Branch stacks are deployed by hand when a change needs one.

## Consequences

`branch-stack.yml` and `run-codebuild.sh` are gone. `template.yaml` gains the two projects,
their role, the trigger function and the custom resource, and it reads the VPC id and the
connect policy from SSM, which the data tier must publish. `buildspec.yaml` uploads the sources
and substitutes the bucket and the object version into `packaged.yaml`, as the other services
substitute their build timestamp. The buildspecs take their password from
`aws rds generate-db-auth-token` and read the user name from SSM. Registering the repository
in the master pipelines of each account is the remaining step outside this repository.

#!/usr/bin/env bash
# Starts a CodeBuild project with environment overrides and waits for it to finish.
# Usage: run-codebuild.sh <project-name> [NAME=value ...]
set -euo pipefail

project="$1"
shift

overrides=()
for assignment in "$@"; do
  overrides+=("name=${assignment%%=*},value=${assignment#*=},type=PLAINTEXT")
done

if [ ${#overrides[@]} -gt 0 ]; then
  build_id=$(aws codebuild start-build --project-name "$project" \
    --environment-variables-override "${overrides[@]}" --query build.id --output text)
else
  build_id=$(aws codebuild start-build --project-name "$project" --query build.id --output text)
fi
echo "Started ${build_id}"

while true; do
  status=$(aws codebuild batch-get-builds --ids "$build_id" --query 'builds[0].buildStatus' --output text)
  case "$status" in
    IN_PROGRESS) sleep 15 ;;
    SUCCEEDED) echo "Finished ${build_id}: ${status}"; exit 0 ;;
    *) echo "Finished ${build_id}: ${status}" >&2; exit 1 ;;
  esac
done

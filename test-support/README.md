# test-support

Test helpers shared by the tests of several modules: today the tokens signed for the mock OIDC
issuer. They live in a plain module because Gradle's configuration cache cannot serialise the
test-fixture classpaths of Quarkus modules. Nothing here is deployed.

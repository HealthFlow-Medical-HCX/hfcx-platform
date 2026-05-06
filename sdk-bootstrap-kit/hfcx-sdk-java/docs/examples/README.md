# Examples

J1 ships only the `BootstrapSmoke` main in `hfcx-sdk-examples/`.

J7 fills this directory with three runnable example projects:

- `submit-claim-example/` — sender flow end-to-end against the platform
- `recipient-spring-boot-example/` — Spring Boot app receiving claims
- `eligibility-check-example/` — the simplest sender flow, useful for
  smoke-testing a fresh integration

Until J7 lands, see `hfcx-sdk-examples/src/main/java/eg/gov/healthflow/hfcx/sdk/examples/BootstrapSmoke.java`.

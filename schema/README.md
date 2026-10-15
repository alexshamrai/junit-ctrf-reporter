# Pinned CTRF schema

`ctrf.schema.json` is the official CTRF JSON schema, version 0.2.0, copied byte for byte from
[`ctrf-io/ctrf`](https://github.com/ctrf-io/ctrf):

- file: `schema/ctrf.schema.json`
- tag: `v0.2.0`
- commit: `e325eb33c7662a208bfa4d093a41cc55439d6e6a`
- SHA-256: `cb9bd075beb0e046076046793f96bb1ba72bd1b1d331b1fbd42ac0ea36776cf3`

The tests load it as a test resource: `CtrfSchemaConformanceTest` in the main module, and
`CtrfSchemaValidationTest` in `integration-ctrf-validator`. It is not part of the published library.

## Updating to a new CTRF version

Replace `ctrf.schema.json` with the file from the new tag, and in the same commit:

1. change `CtrfJson.SPEC_VERSION` to the new version;
2. update the tag, commit and SHA-256 above;
3. update the version in the docs: the first line of the Features list in the root `README.md` (the version and the schema link's tag), and the "Schema" paragraph in `CLAUDE.md`;
4. run `./gradlew :build` and the integration validator, and fix what the new schema rejects.

Never edit the schema by hand.

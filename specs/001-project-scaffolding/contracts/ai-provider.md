# Contract: AI Provider Configuration & Verification

**Feature**: [Project Scaffolding](../spec.md) | **Serves**: FR-018 – FR-023, SC-008, SC-009 | **Date**: 2026-08-13

Azure OpenAI is configured in this feature but not used. No chat, no embeddings, no ingestion. The
only thing that ever contacts Azure is the opt-in verification below, and only when a developer
runs it.

## Configuration binding

Environment variable names are fixed by constitution v1.3.0 and MUST NOT be renamed. Spring
property names were read from `META-INF/spring-configuration-metadata.json` inside
`spring-ai-autoconfigure-model-azure-openai-1.1.8.jar` — they are verified, not assumed.

| Environment variable | Spring property | Secret | Required in this feature |
|---|---|---|---|
| `AZURE_OPEN_AI_KEY` | `spring.ai.azure.openai.api-key` | **Yes** | No |
| `AZURE_OPEN_AI_ENDPOINT` | `spring.ai.azure.openai.endpoint` | No | No |
| `AZURE_OPEN_AI_DEPLOYMENT_NAME` | `spring.ai.azure.openai.chat.options.deployment-name` | No | No |
| `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` | `spring.ai.azure.openai.embedding.options.deployment-name` | No | No — unset is permitted (FR-023) |

Each binds with an empty default (`${VAR:}`) so that absence is a configuration state, not a
startup failure.

### Auto-configuration is off by default

```yaml
spring.ai.model.chat: none
spring.ai.model.embedding: none
spring.ai.model.image: none
spring.ai.model.audio.transcription: none
```

**This is load-bearing, not tidiness — and all four gates are required, not just chat and
embedding.** `spring-ai-autoconfigure-model-azure-openai-1.1.8.jar` registers *four* auto-configuration
classes (`AzureOpenAiChatAutoConfiguration`, `AzureOpenAiEmbeddingAutoConfiguration`,
`AzureOpenAiImageAutoConfiguration`, `AzureOpenAiAudioTranscriptionAutoConfiguration`), and every one
of them is `@ConditionalOnProperty` on its own `spring.ai.model.*` key, each **with
`matchIfMissing=true`** — verified by disassembling all four classes. Each one that activates
`@Import`s the same `AzureOpenAiClientBuilderConfiguration`. Gating only `chat` and `embedding` —
the two this feature discusses — leaves `image` and `audio.transcription` unset, and
`matchIfMissing=true` activates one of them anyway, importing the client builder configuration
exactly as if no gate existed at all. **This was caught empirically during implementation**: a
context-loads test passed in isolation (chat/embedding gated) but failed once run alongside other
tests in the same suite, because the failure depends on which of the four model auto-configurations
Spring resolves as unconditioned first — order-dependent, and easy to miss with only two of the four
gated. All four MUST be set for FR-019 to hold unconditionally.

It delegates to `AzureOpenAiClientBuilderConfiguration`, whose first act when no OpenAI-style key is
present is:

```text
Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty")
```

With credentials absent that throws `IllegalArgumentException` during context refresh and the
application does not start — a direct FR-019 violation. With the gate at `none` the starter stays
on the classpath and inert, and **no `ChatModel` or `EmbeddingModel` bean exists at runtime in this
feature**.

The ingestion feature enables it by flipping these two properties. No dependency change.

### Rejected alternative: placeholder default values

Recurring proposal: instead of gating auto-configuration off, give the variables non-empty defaults
(`${AZURE_OPEN_AI_KEY:dummy}`, `${AZURE_OPEN_AI_ENDPOINT:https://placeholder…}`) so the beans build
and the application starts. Recorded here because it has been raised more than once and the reasons
against it are not obvious from the outside.

It would satisfy FR-019. Disassembly of `AzureOpenAiClientBuilderConfiguration` confirms the
bean performs **no network I/O** and never validates the key — it wraps whatever string it is given
in `AzureKeyCredential`. `Endpoint must not be empty` is the only startup-time check, and any
non-blank string passes it. A placeholder configuration boots cleanly and fails at first use.

It is rejected on four grounds:

1. **It breaks FR-020 and FR-021.** The completeness rule below tests for blank. Default every
   field to a placeholder and `api-key`, `endpoint` and the chat deployment name are non-blank
   always, so the health indicator reports `UP` unconditionally and can never report the truth.
   Repairing that means comparing bound values against magic sentinel strings — strictly worse than
   testing for blank.
2. **Half-defaults silently switch to Managed Identity.** The same disassembly shows the branch
   taken when the endpoint is non-blank and the key is blank: `DefaultAzureCredentialBuilder`. That
   does not fail. It walks the credential chain (environment service principal → Azure CLI login →
   IMDS) and produces, at first request, an error about credential discovery that never mentions the
   missing key. Undiscoverable in practice.
3. **A phantom bean is injectable.** Under the placeholder scheme a functional-looking
   `AzureOpenAiChatModel` exists, and whatever prompt content the first caller sends is transmitted
   to whatever host the placeholder endpoint resolves to. A non-Microsoft placeholder value makes
   that real data egress. Under the gate there is nothing to misuse.
4. **It normalises a committed value in the key position.** Constitution v1.3.0 blocks a committed
   key at review. A placeholder is not a secret, but it stops the diff line changing shape when a
   real key is eventually pasted over it.

**We do default all four values — to the empty string.** That is the version of this idea that
works: empty makes "unconfigured" a representable state that FR-021 can detect, and paired with the
`none` gate the `Assert.hasText` never executes because the bean is never built. Identical boot
behaviour, without the phantom bean, the sentinel strings, or the egress.

One related fact worth knowing when reading Spring AI's own defaults:
`AzureOpenAiChatProperties.DEFAULT_DEPLOYMENT_NAME` is `"gpt-4o"`. Spring AI's copy of the
deployment name is therefore never blank, which is why the completeness rule reads **our** bound
`AzureOpenAiProperties` rather than Spring AI's.

> **Note for secret scanning**: the literal `gpt-4o` above is Spring AI's published default
> constant, read from the library, and is unrelated to any deployment anyone has provisioned. It
> will collide with `AZURE_OPEN_AI_DEPLOYMENT_NAME` for anyone who named their deployment after the
> model. That is a false positive, not a committed configuration value.

## Configuration completeness

Definition (FR-021), used by both the health indicator and the verification:

> Configuration is **complete** when `api-key`, `endpoint` and the **chat** deployment name are
> all present and non-blank. Otherwise it is **incomplete**.

The embedding deployment name is deliberately excluded from this definition — FR-023 permits it to
be unset in this feature. It is reported separately by the verification.

Partial configuration (for example an endpoint with no key) is **incomplete**, never complete. A
half-set environment must not be able to present itself as working. Every combination is covered
by the same rule — there is no case-by-case list to maintain:

| Key | Endpoint | Chat deployment | Result |
|---|---|---|---|
| ✅ | ✅ | ✅ | Complete |
| any other combination of the three (5 remaining, including chat-deployment-only or embedding-set-but-chat-missing) | | | Incomplete |

(Embedding deployment name is not one of the three inputs to this table — see above.)

## Health contribution

Contributes an `azureOpenAi` component to `GET /actuator/health`. See
[health-api.md](health-api.md) for the full response contract.

| Configuration state | Component status | Effect on overall status |
|---|---|---|
| Complete | `UP` | None |
| Incomplete or absent | `UNKNOWN` | **None** — overall stays `UP`, HTTP 200 |

**Guaranteed by contract**:

- The indicator performs **no network I/O**. It never contacts Azure, so it costs nothing, cannot
  be slowed by Azure, and cannot fail because Azure is down (FR-020).
- It never reports `DOWN`. Spring Boot's default severity ordering is
  `DOWN, OUT_OF_SERVICE, UP, UNKNOWN`, and the aggregate takes the most severe present — so
  `UNKNOWN` alongside a healthy database still yields overall `UP` and HTTP 200. `DOWN` would push
  the whole service to 503 for any developer without Azure credentials, breaking SC-009.
- **It cannot detect wrong credentials.** Reporting `UP` means "configured", never "working". Only
  the verification below distinguishes the two. This is a deliberate trade, stated so no one reads
  a green health check as proof the credentials are valid.
- The key value is never included in the health response, logs, or any error message.

## On-demand verification

**Command** (SC-008 — one command, exactly one Azure request):

```bash
backend/mvnw test -Pverify-ai
```

A JUnit test tagged `@Tag("azure")`. The default build sets `<excludedGroups>azure</excludedGroups>`
so it never runs in `mvnw test`; the `verify-ai` profile clears the exclusion and selects the tag.

**Guaranteed by contract**:

| Precondition | Outcome |
|---|---|
| Configuration complete, credentials valid | Passes. Exactly **one** completion request against the chat deployment, minimal prompt, low token cap. |
| Configuration complete, credentials wrong | Fails, reporting Azure's own error (status and message) — not a generic "verification failed". |
| Configuration incomplete | Fails immediately, naming which of key / endpoint / chat deployment is missing. No request is made. |
| Embedding deployment name unset | Reported as missing (FR-023). Does not by itself fail the chat verification — nothing consumes embeddings yet. |

It MUST NOT run during application startup, during the health check, or in the default test suite
(FR-022). Those three exclusions are the whole point of the tag.

## What this contract does not cover

- Any embedding call — nothing embeds in this feature
- Any chat request serving a user — Principles III and IV bind from the chat feature onward
- Token accounting, rate limiting, or retry policy — ingestion and chat features
- Provisioning the Azure resources themselves. **The embedding deployment does not yet exist**
  (`AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` is unset in the target environment). Creating it is a
  prerequisite of the ingestion feature, not a task in this one.

---
name: branch-summary
description: >-
  Generates concise, single-depth logical change summaries of a git branch or pull request.
  Use this skill whenever asked to summarize work done in a git branch, generate pull request
  bullet summaries, or explain branch changes using single-depth bullet points of 10-15 words each.
---

# Branch Logical Summary Skill

This skill provides a standardized procedure for analyzing git branch changes and formatting the logical accomplishments into clean, single-depth bullet points with strict 10–15 word count constraints.

---

## Workflow Procedure

### Step 1: Inspect Branch Diffs and History
Compare the current branch against its upstream target (typically `dev` or `main`):
```powershell
# Review commit log on the branch
git log origin/dev..HEAD --oneline

# Review modified and newly added files
git diff --stat origin/dev...HEAD

# Review full logical diff
git diff origin/dev...HEAD
```

### Step 2: Extract Logical Architectural Changes
Group the code modifications into distinct conceptual themes rather than mechanical line edits:
* Protocol and routing changes (endpoints, brokers, APIs)
* Data structures and state management
* Security and authentication boundaries
* Error handling and exception translations
* Test suites and verification fixtures

### Step 3: Enforce Summary Constraints
Every bullet point in the generated summary MUST adhere to:
1. **Single Depth**: No sub-bullets, nested lists, or multi-level indentations.
2. **Word Count Window**: Strictly **10 to 15 words** per bullet point (inclusive). Count words internally.
3. **Clean Output**: Do NOT include word counts or parenthetical annotations (e.g. `*(14 words)*`) in the final output.
4. **Active Voice**: Start with an active past-tense verb (e.g., *Configured*, *Implemented*, *Registered*, *Blocked*, *Created*).
5. **Logical Focus**: Explain *what* changed and *why* it matters for the system.

---

## Reference Example: STOMP WebSocket Broker (`feat/LT-24`)

* Configured Spring STOMP messaging endpoints to support both standard and lobby connection paths seamlessly.
* Enabled in-memory simple message broker destinations for public topic broadcasts and private user queues.
* Registered dedicated task scheduler to enforce bidirectional ten second heartbeats for active client connections.
* Intercepted connect frames to authenticate incoming JWT bearer tokens before permitting any messaging activity.
* Added fallback token extraction from STOMP passcode headers to support diverse client WebSocket libraries.
* Bound validated player identity and principal attributes directly onto STOMP session storage for handlers.
* Blocked unauthorized subscribe and send frames to ensure unauthenticated sessions cannot access game rooms.
* Implemented custom error handler returning clear descriptive messages within STOMP error frames upon failure.
* Created comprehensive integration tests verifying authentication, rejection handling, and negotiated heartbeats across endpoints.

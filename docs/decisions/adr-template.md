---
# Configuration for the MADR (Markdown Any Decision Records) template.
# Copy this file to NNNN-short-title.md and fill it in. Delete optional sections you don't need.
status: "{proposed | rejected | accepted | deprecated | superseded by ADR-NNNN}"
date: { YYYY-MM-DD when the decision was last updated }
decision-makers: { list everyone involved in the decision }
consulted: { optional — subject-matter experts consulted, two-way communication }
informed: { optional — people kept up-to-date, one-way communication }
---

# {short title, representative of the solved problem and the chosen solution}

## Context and Problem Statement

{Describe the context and problem in 2-3 sentences. Articulate the problem as a question.
Link the PRD(s) / architecture doc(s) this decision relates to.}

## Decision Drivers

<!-- optional -->

- {decision driver 1, e.g. a force, a quality goal, a constraint}
- {decision driver 2}

## Considered Options

- {option 1}
- {option 2}
- {option 3}

## Decision Outcome

Chosen option: "{option N}", because {justification — e.g. resolves the driver | is the only
option that meets a hard constraint | best trade-off}.

### Consequences

<!-- optional -->

- Good, because {positive consequence}
- Bad, because {negative consequence we knowingly accept}

### Confirmation

<!-- how do we verify this decision is actually implemented and stays implemented? -->

{The enforceable check: an arch-test (cf. `ApiPrefixArchitectureTest`), a lint rule, a contract
test, a code review item. A decision without a Confirmation is just prose — name the check.}

## Pros and Cons of the Options

<!-- optional but recommended — this is what makes weighed alternatives durable -->

### {option 1}

{optional — example / description / pointer to more info}

- Good, because {argument}
- Neutral, because {argument}
- Bad, because {argument}

### {option 2}

- Good, because {argument}
- Bad, because {argument}

## Future possibilities

<!-- optional — the sanctioned parking lot (Rust-RFC style). -->
<!-- Ideas out of scope NOW but related. NOTE: nothing here is a commitment, and an item -->
<!-- in this section is never on its own a reason to accept the decision above. -->

- {idea for later}

## More Information

<!-- optional -->

{Evidence, links, the originating PRD's D-lock this MADR was extracted from, related MADRs.}

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/ContextRelevanceScorer.java
    - src/main/java/org/specdriven/agent/agent/KeywordContextRelevanceScorer.java
  tests:
    - src/test/java/org/specdriven/agent/agent/ContextRelevanceScorerTest.java
---

# Context Relevance Scorer

## ADDED Requirements

### Requirement: ContextRelevanceScorer interface
The system MUST provide a `ContextRelevanceScorer` contract for computing how relevant a prior tool result is to the current turn.

#### Scenario: Score a tool result against the current turn
- GIVEN a scorer implementation
- AND the current turn contains user-visible text and zero or more requested tool calls
- WHEN the caller asks the scorer to evaluate a prior tool result
- THEN the scorer MUST return a deterministic numeric relevance score

#### Scenario: Deterministic result for identical inputs
- GIVEN the same current-turn text, requested tool-call names, and prior tool result
- WHEN the scorer is invoked repeatedly
- THEN it MUST return the same score each time

### Requirement: Default keyword-based scorer
The system MUST provide a default `KeywordContextRelevanceScorer` implementation that uses observable keyword and tool-name overlap instead of provider-specific semantic embeddings.

#### Scenario: Matching tool name increases relevance
- GIVEN a prior tool result produced by tool `grep`
- AND the current turn references tool `grep`
- WHEN the default scorer evaluates that tool result
- THEN its score MUST be higher than the score for an otherwise identical tool result whose tool name is not referenced by the current turn

#### Scenario: Matching text keywords increases relevance
- GIVEN two prior tool results from the same tool
- AND one tool result contains keywords also present in the current turn text
- WHEN the default scorer evaluates both results
- THEN the overlapping result MUST receive the higher score

#### Scenario: No overlap yields lowest relevance band
- GIVEN a prior tool result whose tool name and text have no keyword overlap with the current turn
- WHEN the default scorer evaluates that result
- THEN it MUST return the lowest relevance band used by the scorer

### Requirement: Current-turn inputs are explicit
The scorer input model MUST make the current turn observable through explicit turn text and requested tool-call names rather than requiring direct access to internal orchestrator state.

#### Scenario: Score without requested tool calls
- GIVEN current-turn text with no requested tool calls
- WHEN the scorer evaluates a prior tool result
- THEN the score MUST still be computed from the current-turn text alone

#### Scenario: Score without current-turn text
- GIVEN an empty current-turn text
- AND one or more requested tool-call names
- WHEN the scorer evaluates a prior tool result
- THEN the score MUST still be computed from the requested tool-call names

### Requirement: Score ordering is usable for later filtering
The scorer output MUST support later changes sorting candidate tool results by descending relevance without additional hidden metadata.

#### Scenario: Higher score ranks first
- GIVEN multiple prior tool results with different relevance scores
- WHEN the results are sorted by score descending
- THEN the most relevant result MUST sort ahead of less relevant results

#### Scenario: Ties are stable
- GIVEN multiple prior tool results that receive the same score
- WHEN the caller preserves original input order for equal-score items
- THEN the scorer MUST not require any extra tie-breaker information to keep the ordering stable

### Requirement: Default scorer text normalization
The default scorer MUST normalize case and ignore punctuation-only differences when comparing keywords.

#### Scenario: Case differences do not affect matches
- GIVEN current-turn text containing `BASH`
- AND a prior tool result containing `bash`
- WHEN the default scorer evaluates the result
- THEN the keyword match outcome MUST be the same as if both texts had the same case

#### Scenario: Punctuation-only differences do not affect matches
- GIVEN current-turn text containing `build-status`
- AND a prior tool result containing `build status`
- WHEN the default scorer evaluates the result
- THEN punctuation-only differences MUST NOT prevent the keyword match

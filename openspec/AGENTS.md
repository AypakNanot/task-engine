# OpenSpec Agent Conventions

## Overview

This document defines conventions for using OpenSpec in the Task Engine project.

## Directory Structure

```
openspec/
├── project.md              # Project overview and context
├── AGENTS.md               # This file - agent conventions
├── specs/                  # Approved specifications (immutable)
│   └── {capability}/
│       └── spec.md
└── changes/                # Proposed changes (mutable until approved)
    └── {change-id}/
        ├── proposal.md     # Change summary and motivation
        ├── design.md       # Architecture and trade-offs
        ├── tasks.md        # Ordered implementation tasks
        └── specs/          # Spec deltas (ADDED/MODIFIED/REMOVED)
            └── {capability}/
                └── spec.md
```

## Change-ID Naming Convention

Change IDs must be verb-led and descriptive:
- `implement-{feature}` - Adding new capability
- `fix-{issue}` - Bug fixes
- `refactor-{component}` - Structural improvements
- `enhance-{capability}` - Improvements to existing feature

## Spec Format Requirements

Each spec delta must use structured format:

```markdown
# Spec: {Capability Name}

## ADDED Requirements
### Requirement: {Requirement Title}
#### Scenario: {Scenario Name}
**Given** {precondition}
**When** {action}
**Then** {outcome}
**And** {additional outcome}

## MODIFIED Requirements
### Requirement: {Existing Requirement}
... (changes marked inline)

## REMOVED Requirements
### Requirement: {Deprecated Requirement}
... (reason for removal)
```

## Validation Rules

1. Every Requirement must have at least one Scenario
2. Scenarios must use Given/When/Then format
3. Spec deltas must reference related capabilities when applicable
4. Tasks must be ordered and include validation criteria
5. Design.md required for multi-system changes or new patterns

## Approval Process

1. Run `openspec validate {change-id} --strict`
2. Resolve all validation errors
3. Submit proposal for review
4. Upon approval, apply changes with `openspec:apply`
5. Spec deltas move to `openspec/specs/` after approval
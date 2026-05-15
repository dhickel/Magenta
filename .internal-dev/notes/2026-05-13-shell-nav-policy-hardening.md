# Deferred Idea
Create a small shared shell-navigation helper/policy for controller shells that serve full HTML documents, so top nav links default to full-page anchors and require explicit opt-in for HTMX route swaps.

# Why Deferred
The current fix addresses the immediate production regression with minimal blast radius. Policy extraction is useful but not required to restore correct behavior.

# Potential Scope
- Shared helper or factory method for `TopNavBuilder`.
- Optional test helper that asserts no `hx-get` attributes on shell-level nav links.
